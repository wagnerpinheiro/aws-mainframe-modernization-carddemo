# Phase 1 Pilot Playbook — CBTRN01C → TransactionValidationJob

**Date:** 2026-09-13  
**Pilot unit:** CBTRN01C (494 LOC) → `TransactionValidationJob`  
**Status:** 5/5 characterization tests GREEN; `mvn test` BUILD SUCCESS

---

## What the pilot found (surprises vs. the brief)

### S1 — CBTRN01C does NOT apply reject codes 100–103

The brief described CBTRN01C as "applies reject rules 100/101/102/103, writes accepted transactions to ledger and rejects to DALYREJS." **This is wrong.** CBTRN01C is a read-only validator:
- It reads DALYTRAN sequentially and validates card exists in XREF, account exists in ACCTFILE
- It DISPLAYs results to stdout only — no file writes
- RULE-063 through RULE-066 (reject codes 100–103) are in **CBTRN02C** (`CBTRN02C.cbl:380–420`)
- DALYREJS is written only by CBTRN02C, not CBTRN01C

**Impact on Phase 1:** No impact on CBTRN02C scope. The brief's Phase 1 exit criteria for RULE-063 through RULE-066 correctly target CBTRN02C.

**Impact on the brief:** The description in §2 (Legacy→Target table, row CBTRN01C) should be updated to reflect the actual behavior before Phase 2 begins.

### S2 — Three dead file opens in CBTRN01C

CUSTOMER-FILE, CARD-FILE, and TRANSACT-FILE are opened INPUT but never read. These are not bugs — they are vestigial code from an earlier version. No Java equivalents were created for them.

### S3 — GnuCOBOL compiles the legacy source

`cobc -fsyntax-only -I app/cpy/ CBTRN01C.cbl` passes clean. This is an unexpected bonus — more of the legacy code may be locally compilable than the PoC premise (P3) assumed. Worth attempting syntax checks on CBTRN02C and CBACT04C before claiming equivalence is purely trace-based.

---

## Decisions made during the pilot

| Decision | Choice | Rationale |
|---|---|---|
| Q6 reject file format | CSV: `transactionId,cardNumber,resolvedAccountId,status` | Simple, grep-able, survives re-ingestion; format is agnostic to header order in tests |
| Writer lifecycle | `ItemStreamWriter` (not plain `ItemWriter`) | Required for `open()` / `close()` to create and flush the reject file per step run |
| Sign-overpunch parsing | `CobolDisplayParser.parseSignedAmount()` in `carddemo-common` | Shared utility — CBTRN02C and CBACT04C will need it for DALYTRAN-AMT and balance fields |
| Package for batch EOD | `com.carddemo.batch.eod` | Keeps jobs isolated from online (`carddemo-web`) and domain modules |
| Chunk size | 100 records / chunk | Default; revisit for CBTRN02C which writes to ACCOUNT_FILE (lock contention risk) |
| H2 keyword clash | `NON_KEYWORDS=VALUE` in JDBC URL | `VALUE` is a reserved H2 keyword that clashes with COBOL amount field names |

---

## Patterns for remaining Phase 1 batch programs (CBTRN02C, CBACT04C)

### Project structure convention
```
carddemo-batch-eod/
└── src/main/java/com/carddemo/batch/eod/
    ├── <ProgramName>JobConfig.java     ← Spring Batch Job + Step beans
    ├── <ProgramName>Processor.java     ← ItemProcessor<In, Out>
    ├── <ProgramName>Writer.java        ← ItemStreamWriter<Out> if file output
    └── <domain records>.java           ← Java records for in/out types
```

### Reader pattern (fixed-width flat file)
Use `FlatFileItemReaderBuilder` with `FixedLengthTokenizer` and inline `fieldSetMapper`. Field ranges come from the relevant copybook (CVACT01Y, CVTRA06Y, etc.). Always verify ranges against actual file with `wc -c` and sample data inspection. Always set `tokenizer.setStrict(false)` to tolerate lines shorter than the nominal record length (FILLER trailing spaces may be absent in ASCII files).

### Signed decimal parsing
`CobolDisplayParser.parseSignedAmount(rawString, impliedDecimals)` handles all 20 zone-overpunch characters. Use for any `PIC S9(n)Vmm` field. Do NOT call `FieldSet.readBigDecimal()` on overpunched fields — it will throw a NumberFormatException on the sign character.

### Writer pattern (file output)
Implement `ItemStreamWriter<T>`. Override `open()` to create the output file + write header. Override `write()` to append rows and flush after each chunk. Override `close()` to close the `PrintWriter`. This ensures correct behavior across multiple job runs in the same JVM (test scenario) and across chunk boundaries.

### Test pattern
Use `@SpringBatchTest @SpringBootTest @ActiveProfiles("test")`. Override the reader's input resource via a static `AtomicReference<Resource>` + inner `@TestConfiguration @StepScope @Primary` bean override (enable with `spring.main.allow-bean-definition-overriding=true`). Use `SyntheticDalytranBuilder` for synthetic input; `FixtureLoader` for full fixture seeding. Clean state in `@BeforeEach` via `jobRepositoryTestUtils.removeJobExecutions()` + repository `deleteAll()`.

### Characterization test naming convention
- `<happy path>_<descriptor>` — e.g., `fullFixtureRun_allValid`
- `<trigger>_<outcome>` — e.g., `cardNotFoundGoesToReject`, `overlimitGoesToReject`
- `emptyFileCompletesCleanly` — always test EOF on first read

---

## CBTRN02C — what to expect

CBTRN02C (`legacy/CardDemo/app/cbl/CBTRN02C.cbl`, 731 LOC per brief) is the actual transaction posting program:
- Reads DALYTRAN sequentially (same format, same reader config)
- For each record: apply reject codes 100–103 (RULE-063 through RULE-066) — card in XREF, account exists, credit limit check, expiry date check
- Posts valid transactions: updates ACCOUNT-FILE balance + cycle accumulators, writes to TRANSACT-FILE, updates TCATBALF category balances
- Writes rejects to DALYREJS

**New entities needed (not yet in `carddemo-domain`):**
- `TransactionEntity` + `TransactionRepository` (TRANSACT VSAM)
- `TranCatBalanceEntity` + `TranCatBalanceRepository` (TCATBALF)

**Risk 1 (from brief):** `@Transactional(isolation = SERIALIZABLE)` on the item writer to replicate VSAM exclusive lock during posting run.

**Risk 2 (from brief):** Credit limit formula (RULE-065) uses cycle-to-date only: `WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT − ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT`. Implemented exactly; TODO comment marks production gap.

---

## Phase 1 exit criteria status

| Criterion | Status |
|---|---|
| `TransactionValidationJob` compiles with `mvn compile` | ✅ |
| JUnit 5 characterization tests pass — 5/5 | ✅ |
| `mvn test` BUILD SUCCESS | ✅ |
| RULE-063 (card in XREF) tested | ✅ `cardNotFoundGoesToReject` |
| RULE-064 (account exists) tested | ✅ `accountNotFoundGoesToReject` |
| Spring Boot application context starts | ✅ (Spring Batch auto-config, H2 in-memory) |
| PHASE1_PLAYBOOK.md written | ✅ this document |
| RULE-007 (RoundingMode.DOWN) tested + documented | ✅ `InterestCalculationJobTest#rule007_roundingDown_notHalfUp` |
| RULE-008 (balance updated after interest) | ✅ `#rule008_accountBalanceUpdated` |
| RULE-009 (cycle accumulators reset) | ✅ `#rule009_cycleAccumulatorsReset` |
| RULE-059 (inactive account not blocked) | ✅ `TransactionPostingJobTest#rule059_inactiveAccountIsPosted` |
| RULE-061 (atomic rollback) | ✅ `#rule061_atomicRollback` |
| RULE-065 (overlimit cycle-to-date formula) | ✅ `#rejectCode102_overlimit` |
| RULE-066 (expiry strict parsing) | ✅ `#rejectCode103_accountExpired` |
| `TransactionPostingJob` compiles and tests pass | ✅ 10/10 |
| `InterestCalculationJob` compiles and tests pass | ✅ 8/8 |
| `mvn spring-boot:run` exits without error | ✅ started in 1.1s |
| All Phase 1 tests: `mvn test` | ✅ **25/25 GREEN** |

**Phase 1 is COMPLETE.** All exit criteria are met.

---

## CBACT04C findings

### S4 — Account groupId is blank in fixture accounts

All fixture accounts have blank ACCT-GROUP-ID (`""`). The DISCGRP lookup always falls
back to the DEFAULT group (status '23' → `MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID`).
The `A000000000` group in discgrp.txt is not exercised by the fixture data.
The ZEROAPR group has all-zero rates (no interest).

### S5 — TCATBAL is read SEQUENTIALLY with account-change detection

CBACT04C opens TCATBAL-FILE with `ACCESS MODE IS SEQUENTIAL` (not RANDOM). The program
detects account changes by comparing `TRANCAT-ACCT-ID` with `WS-LAST-ACCT-NUM`. This
stateful accumulation pattern requires a Tasklet (not a chunk-oriented Step) in Spring Batch.

### S6 — 1400-COMPUTE-FEES is a no-op ("To be implemented")

Paragraph 1400-COMPUTE-FEES contains only `EXIT`. No fee computation exists in the legacy.
Not translated; documented in `InterestCalculationTasklet` comment.
