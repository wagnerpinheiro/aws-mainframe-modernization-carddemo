# TRANSFORMATION NOTES — CBTRN01C → TransactionValidationJob

**Legacy:** `legacy/CardDemo/app/cbl/CBTRN01C.cbl` (494 LOC)  
**Target:** `carddemo-batch-eod` module — `TransactionValidationJob`  
**Date:** 2026-09-13  
**Status:** 5/5 characterization tests GREEN

---

## Behavior Mapping

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| CBTRN01C.cbl:155–197 | `MAIN-PARA` — sequential read loop + open/close all files | `TransactionValidationJobConfig.java` | Spring Batch `Job` + `Step` declaration |
| CBTRN01C.cbl:202–225 | `1000-DALYTRAN-GET-NEXT` — sequential read + EOF detection + abend on bad status | `TransactionValidationJobConfig.java:dailyTranReader()` | `FlatFileItemReader` with `FixedLengthTokenizer` |
| CBTRN01C.cbl:227–239 | `2000-LOOKUP-XREF` — `READ XREF-FILE KEY IS FD-XREF-CARD-NUM INVALID KEY` | `TransactionValidationProcessor.java:process()` L34–40 | `cardXRefRepository.findById(record.cardNumber())` |
| CBTRN01C.cbl:241–250 | `3000-READ-ACCOUNT` — `READ ACCOUNT-FILE KEY IS FD-ACCT-ID INVALID KEY` | `TransactionValidationProcessor.java:process()` L42–50 | `accountRepository.findById(accountId)` |
| CBTRN01C.cbl:181–183 | `DISPLAY "CARD NUMBER … COULD NOT BE VERIFIED. SKIPPING"` | `TransactionValidationProcessor.java:process()` | `log.warn(...)` + `ValidationStatus.CARD_NOT_FOUND` |
| CBTRN01C.cbl:177–178 | `DISPLAY "ACCOUNT … NOT FOUND"` | `TransactionValidationProcessor.java:process()` | `log.warn(...)` + `ValidationStatus.ACCOUNT_NOT_FOUND` |
| CBTRN01C.cbl:252–359 | `0000-DALYTRAN-OPEN` … `0500-TRANFILE-OPEN` — file open paragraphs | `TransactionValidationJobConfig.java` | Spring Batch step lifecycle (`open()`, batch infrastructure) |
| CBTRN01C.cbl:361–467 | `9000-DALYTRAN-CLOSE` … `9500-TRANFILE-CLOSE` — file close paragraphs | `TransactionValidationWriter.java:close()` | `rejectWriter.close()` |
| CBTRN01C.cbl:469–473 | `Z-ABEND-PROGRAM` — calls `CEE3ABD` | Spring Batch step framework | Unhandled `ItemStreamException` / `RuntimeException` → `FAILED` step status |
| CBTRN01C.cbl:475–489 | `Z-DISPLAY-IO-STATUS` — file status display | `FlatFileItemReader` error handling | Spring Batch logs `FlatFileParseException` with line number |

---

## Deliberate Deviations from Legacy Behavior

| # | Deviation | Rationale |
|---|---|---|
| **1** | **Reject CSV file added (Q6 enhancement).** COBOL outputs errors to DISPLAY only. Java writes a structured CSV reject file to `carddemo.batch.reject-output`. | Q6 established premise: no COBOL reject file exists; structured output is an explicit PoC improvement. Header: `transactionId,cardNumber,resolvedAccountId,status`. |
| **2** | **Dead file opens removed.** COBOL opens CUSTOMER-FILE, CARD-FILE, TRANSACT-FILE as INPUT but never reads them (lines 271–359). Java declares no repositories or readers for these files. | Pure dead code elimination — no behavior is lost. |
| **3** | **Abend replaced by Spring Batch FAILED status.** `Z-ABEND-PROGRAM` calls `CEE3ABD` (z/OS abend). In Java, an unrecoverable I/O error on the input file propagates as `ItemStreamException`, which Spring Batch records as a `FAILED` step with full context. | No CEE3ABD equivalent in JVM; exception propagation is strictly safer — provides a restart checkpoint rather than a hard crash. |
| **4** | **TIMING/ABCODE fields not translated.** COBOL uses `PIC S9(9) BINARY` fields `TIMING` and `ABCODE` as abend parameters. Not present in Java. | Used only in the abend path (deviation #3); no business logic depends on them. |

---

## What Was NOT Migrated (Dead Code / Out of Scope)

| Item | Reason |
|---|---|
| CUSTOMER-FILE open/read/close | Opened INPUT but never used in any READ or WRITE statement |
| CARD-FILE open/read/close | Same — dead open |
| TRANSACT-FILE open/read/close | Opened INPUT but unused (posting logic is in CBTRN02C) |
| `TWO-BYTES-BINARY` / `TWO-BYTES-ALPHA` REDEFINES | Used only in `Z-DISPLAY-IO-STATUS` for binary→decimal conversion of VSAM file status codes; replaced by Spring Batch's built-in `FlatFileParseException` |
| `TIMING`, `ABCODE`, `APPL-RESULT` working-storage | Abend-path bookkeeping; see deviation #3 |
| Reject codes 100–103 (RULE-063 through RULE-066) | These are in **CBTRN02C**, not CBTRN01C. Confirmed in business rules catalog (`CBTRN02C.cbl:380–420`). CBTRN01C only logs; it does not apply structured reject codes or write to DALYREJS. |

---

## RULE-063 / RULE-064 implementation notes

The brief attributed RULE-063 through RULE-066 to CBTRN01C. Static analysis of the source and the business rules catalog (`BUSINESS_RULES.md:908–960`) confirms these rules are in CBTRN02C. CBTRN01C only validates card existence in XREF (→ `ValidationStatus.CARD_NOT_FOUND`) and account existence in ACCTFILE (→ `ValidationStatus.ACCOUNT_NOT_FOUND`) — without reject codes. The Java equivalents are named consistently with the business rules catalog to avoid confusion across phases.

---

## Architecture Review Findings (MEDIUM/LOW — not applied)

Applied by critic: **HIGH-1** (`PrintWriter` → `BufferedWriter` in writer), **HIGH-2** (new `TransactionValidationReaderTest` pins production column ranges).

| # | Severity | Finding |
|---|---|---|
| M-1 | MEDIUM | `TransactionValidationWriter` is a singleton holding per-step state (`rejectWriter` field). Works correctly for PoC (defensive `close()` in `open()`), but idiomatically should be `@StepScope`. |
| M-2 | MEDIUM | Restart behavior is implicitly broken: `open()` always truncates the reject file; `update()` is a no-op. A restarted job overwrites rejects from previous partial run. Add `preventRestart()` on the step builder or document the limitation before production use. |
| M-3 | MEDIUM | `CobolDisplayParser.parseSignedAmount` has unreachable `trimmed.isEmpty()` check, and `new BigDecimal(digits)` can throw `NumberFormatException` (not `InvalidDataException`) for interior non-digit characters — mixed exception contract. Wrap the constructor call for production. |
| M-4 | MEDIUM | No direct unit tests for `CobolDisplayParser` — negative amounts (`}`, `J`–`R`), zero-negative (`}`), `parseUnsignedLong`, error cases. Fixture data has no negative amounts, so bugs in the negative-sign path pass all 7 tests. |
| M-5 | MEDIUM | `FixtureLoader.loadFullFixtures` calls `deleteAll()` redundantly (already done in `@BeforeEach`). Harmless at 50 rows; document assumption or remove duplication. |
| M-6 | MEDIUM | `TEST_INPUT` static `AtomicReference` has no ordering guard — add `@Execution(ExecutionMode.SAME_THREAD)` to prevent accidental parallel test execution. |
| N-1 | Nit | `DailyTransactionRecord` stores timestamps as raw `String` — defensible (mirrors COBOL), worth a comment. |
| N-2 | Nit | `OverridableReaderConfig` javadoc claims to be the column-range oracle — no longer true after HIGH-2 fix. Update comment to reference `TransactionValidationReaderTest`. |
| N-3 | Nit | `parseUnsignedLong` uses `strip()` (Unicode); `parseSignedAmount` uses `stripTrailing()`. Asymmetric — standardize to `stripTrailing()` for ASCII-only COBOL data. |

---

## Follow-ups for CBTRN02C (next module in Phase 1 scope)

1. **DALYREJS reject file** — CBTRN02C writes structured rejects with codes 100–103 (RULE-063 through RULE-066). The Java `TransactionValidationJob` reject CSV can be consolidated with CBTRN02C's reject output, or kept separate. Decision needed before Phase 1 exits.
2. **`ACCTDATA` exclusive lock during CBTRN02C** — Brief risk 1: use `@Transactional(isolation = SERIALIZABLE)` on the item writer; document in PHASE1_PLAYBOOK.md.
3. **`TransactionPostingJob` needs `TransactionEntity`** — CBTRN02C writes to TRANSACT VSAM. Requires `TransactionEntity` + `TransactionRepository` in `carddemo-domain` (not yet created — CBTRN01C only reads XREF and ACCOUNT).
4. **Chunk size for CBTRN02C** — batch job updates `ACCTDATA` and `TCATBALF` per transaction. Chunk size of 100 should be validated against lock contention in a concurrent H2 test before proceeding to Phase 2.
