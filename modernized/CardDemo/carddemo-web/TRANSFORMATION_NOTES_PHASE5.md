# TRANSFORMATION NOTES — Phase 5: Transactions & Billing Online

**Legacy programs:** `COBIL00C.cbl` (572 LOC) · `COTRN00C.cbl` · `COTRN01C.cbl` · `COTRN02C.cbl` (612 LOC) · `CORPT00C.cbl` (649 LOC)  
**Target module:** `carddemo-web` — `billing/`, `transaction/`, `report/` packages  
**Date:** 2026-09-13  
**Status:** 13/13 characterization tests GREEN

---

## Behavior Mapping

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| COBIL00C.cbl:154–244 | `PROCESS-ENTER-KEY` — CONF-PAY-YES branch: READPREV highest TRAN-ID, ADD 1, WRITE TRANSACT, REWRITE ACCTDAT | `BillPaymentService.java:50–83` | `BillPaymentService.pay()` — full-balance payment transaction |
| COBIL00C.cbl:198–204 | `PROCESS-ENTER-KEY` — `IF ACCT-CURR-BAL <= ZEROS` guard | `BillPaymentService.java:55–58` | `ZeroBalanceException` thrown; RULE-011 |
| COBIL00C.cbl:212–219 | `STARTBR-TRANSACT-FILE` / `READPREV-TRANSACT-FILE` / ADD 1 to `WS-TRAN-ID-NUM` | `TransactionIdGenerator.java:46–47` | `TransactionIdGenerator.nextId()` — DB-seeded AtomicLong (RULE-012 fix) |
| COBIL00C.cbl:220–234 | `MOVE ACCT-CURR-BAL TO TRAN-AMT` · hardcoded type '02'/cat 2/source 'POS TERM'/merchant-id 999999999 | `BillPaymentService.java:60–79` | `BillPaymentService.pay()` — RULE-010; same hardcoded constants preserved |
| COBIL00C.cbl:58–71 | `CONFIRMI = 'Y'` / `CONF-PAY-YES` flag | `BillPaymentController.java:77–97` | `BillPaymentController.pay()` — confirmed='Y' guard |
| COBIL00C.cbl:159–167 | `ACTIDINI = SPACES` → blank account error | `BillPaymentController.java:61–64` | `confirm()` — account not-found check via `accountRepository.findById()` |
| COTRN00C.cbl:146–228 | `PROCESS-ENTER-KEY` + `PROCESS-PAGE-FORWARD`/`BACKWARD` — STARTBR / READNEXT / READPREV 10-row paged browse | `TransactionListController.java:34–39` | `TransactionListController.list()` — `transactionService.findByCard(cardNumber)` returns all rows; paging deferred to UI |
| COTRN01C.cbl:144–192 | `PROCESS-ENTER-KEY` — `READ TRANSACT` keyed on TRAN-ID + populate all map fields | `TransactionDetailController.java:22–29` | `TransactionDetailController.detail()` — `transactionService.findById(tranId)` |
| COTRN02C.cbl:193–230 | `VALIDATE-INPUT-KEY-FIELDS` — acct/card blank check; XREF lookup; card-to-account cross-reference | `TransactionService.java:63–88` | `TransactionService.validate()` — RULE-047; `cardXRefRepository.findById()` |
| COTRN02C.cbl:251–319 | `VALIDATE-INPUT-DATA-FIELDS` — type, category, source, desc, amount, dates, merchant all required | `TransactionService.java:64–87` | `TransactionService.validate()` — RULE-048/049/050/052; required-field guards |
| COTRN02C.cbl:339–351 | Amount format check `TRNAMTI(1:1) NOT '+'/'-'`, digits 2–9, decimal at 10 | `TransactionService.java:74–76` | `req.amount().compareTo(BigDecimal.ZERO) <= 0` — sign/format constraint simplified (RULE-049/050) |
| COTRN02C.cbl:353–427 | CSUTLDTC date validation for TORIGDTI / TPROCDTI; error '2513' silently suppressed | `TransactionService.java:80–85` | `LocalDate.parse(req.originDate())` — strict ISO parse; no 2513 suppression (Q11 fix; RULE-054) |
| COTRN02C.cbl:169–188 | `CONFIRMI = 'Y'/'y'` → call `ADD-TRANSACTION`; else show "Confirm to add this transaction..." | `TransactionAddController.java:43–64` / `TransactionAddController.java:67–95` | Two-step POST: step 1 calls `validate()` only; step 2 requires `confirmed=Y` then calls `save()` (RULE-051 / SEC-018) |
| COTRN02C.cbl:442–466 | `ADD-TRANSACTION` — STARTBR/READPREV/ADD 1/WRITE TRANSACT | `TransactionService.java:94–111` | `TransactionService.save()` — `idGenerator.nextId()` + `transactionRepository.save()` (RULE-012) |
| CORPT00C.cbl:462–510 | `SUBMIT-JOB-TO-INTRDR` — iterates JOB-DATA lines up to '/*EOF', writes each record to CICS TDQ 'JOBS' | `ReportTriggerController.java:46–58` | `ReportTriggerController.trigger()` — REST POST /reports/trigger → HTTP 202 + jobExecutionId (Q10) |
| CORPT00C.cbl:212–255 | Monthly/Yearly report type selection; date-range derivation | `ReportTriggerController.java:46–58` / `ReportTriggerRequest.java` | `req.reportType()`, `req.startDate()`, `req.endDate()` passed in request body |
| CORPT00C.cbl:256–436 | Custom date range — SDTMM/SDTDD/SDTYYYY + EDTMM/EDTDD/EDTYYYY fields; CSUTLDTC validation | `ReportTriggerRequest.java` | `startDate` / `endDate` as plain strings (caller validates); CSUTLDTC not needed |

---

## Deliberate Deviations from Legacy Behavior

| # | Deviation | Rationale |
|---|---|---|
| **1** | **DB-seeded AtomicLong replaces READPREV+increment for transaction IDs (RULE-012 fix).** COBOL performs STARTBR (HIGH-VALUES) → READPREV → ENDBR → ADD 1 to derive the next ID. This sequence is not serialized: concurrent online users can derive the same ID, resulting in DUPREC on write (documented defect in BUSINESS_RULES.md). `TransactionIdGenerator` seeds from the current max ID on startup and uses `AtomicLong.getAndIncrement()` — thread-safe within a JVM lifetime. | The COBOL race condition is a confirmed P0 data-integrity defect. AtomicLong eliminates the race while keeping ID format identical (16-char zero-padded numeric). Production note: replace with a database SEQUENCE (NEXTVAL) to survive restarts without gap risk. |
| **2** | **`LocalDate.parse()` replaces CSUTLDTC + error 2513 suppression (Q11 / RULE-054).** COBOL calls `CSUTLDTC` and then checks `IF CSUTLDTC-RESULT-MSG-NUM NOT = '2513'`, silently accepting any date that returns message 2513. The Java target uses `LocalDate.parse(req.originDate())` with ISO-8601 strict mode — any invalid calendar date throws `DateTimeParseException` which is translated to a `ValidationException`. | `CSUTLDTC` is a z/OS utility with no JVM equivalent. The 2513 suppression is classified RULE-054: confidence high (code unambiguous), business intent low (possibly accidental suppression). Strict parse is the safer default; SME must confirm whether any legitimate dates depended on 2513 suppression before relaxing. |
| **3** | **REST POST + HTTP 202 replaces TDQ/JCL reconstruction (Q10).** `CORPT00C` builds a JCL stream at runtime (80-char records in JOB-DATA), iterates to `/*EOF`, and writes each record to CICS extra-partition TDQ 'JOBS'. In Java, `ReportTriggerController.trigger()` accepts a JSON request body and returns HTTP 202 Accepted with a UUID `jobExecutionId`. No JCL is generated. The production TODO is to inject `JobLauncher` and dispatch a Spring Batch `transactionReportJob`. | TDQ + internal reader are z/OS-specific; no direct JVM equivalent exists. Rebuilding JCL in Java would be a liability rather than a migration. REST + async JobLauncher is idiomatic Spring and achieves the same functional outcome (fire-and-forget report generation). The 202 response lets the caller poll status — a capability CORPT00C did not have. |
| **4** | **`TransactionService` split into `validate()` + `save()` (SEC-018 harden finding).** In COTRN02C, the `ADD-TRANSACTION` paragraph runs immediately when `CONFIRMI = 'Y'`. However, security audit SEC-018 found that a malformed POST to the legacy endpoint could commit before the confirmation screen was shown. Java enforces a strict two-step HTTP flow: `POST /transactions/add` calls `validate()` only (no DB write); `POST /transactions/add/confirm` with `confirmed=Y` calls `save()` (which internally calls `validate()` again as a guard). The controller never calls `save()` without an explicit Y confirmation. | Eliminates the SEC-018 bypass. `save()` calling `validate()` internally provides defense-in-depth in case the controller is called out of sequence in future refactors. |
| **5** | **SEC-017: billing confirm/pay endpoints restricted to ROLE_ADMIN.** COBIL00C reads any account by ID passed in the ACTIDINI field without verifying that the signed-on user owns that account. The harden audit classified this as IDOR (Insecure Direct Object Reference). `BillPaymentController.confirm()` and `pay()` are annotated `@PreAuthorize("hasRole('ADMIN')")` as an interim guard. `TransactionListController` carries the same restriction. | The user→account ownership link does not exist in the current schema (no `customer_id` on `app_user`). The restriction prevents unauthorized account access. Removal requires: (a) adding `customer_id` to `app_user`, (b) resolving the associated account via `CardXRefRepository`, and (c) asserting the requested accountId matches the principal's customer. |

---

## What Was NOT Migrated (Dead Code / Out of Scope)

| Item | Reason |
|---|---|
| `WIRTE-JOBSUB-TDQ` paragraph (CORPT00C.cbl:515–535) — `EXEC CICS WRITEQ TD QUEUE('JOBS')` | CICS TDQ internal reader is z/OS-only. Replaced entirely by REST POST + HTTP 202 (deviation #3). |
| JOB-DATA / JOB-DATA-2 JCL embedded in WORKING-STORAGE (CORPT00C.cbl:81–127) — JCL stream for TRNRPT00 job | JCL reconstruction in Java has no value. Spring Batch `JobLauncher` is the idiomatic replacement; wiring deferred to production. |
| `CSUTLDTC` external call (COTRN02C.cbl:393–427, CORPT00C.cbl:392–426) | z/OS date utility with no JVM equivalent. Replaced by `LocalDate.parse()` with strict ISO-8601 validation (deviation #2). |
| COTRN00C `PROCESS-PAGE-FORWARD` / `PROCESS-PAGE-BACKWARD` — STARTBR / READNEXT / READPREV 10-row keyset pagination | Keyset pagination over VSAM is z/OS-specific. Java uses JPA `findAllByCardNumberOrderByIdAsc()` returning all matching rows; browser-side pagination or Pageable is a follow-up task. |
| COTRN02C `COPY-LAST-TRAN-DATA` paragraph (cbl:471–495) — PF5 key: copy last transaction fields into form | PF key mapping has no direct REST/HTML equivalent. The PF5 "copy last" convenience is not present in the Spring MVC UI; it is a non-functional UI shortcut with no business logic impact. |
| COBIL00C `STARTBR-TRANSACT-FILE` + `READPREV-TRANSACT-FILE` + `ENDBR-TRANSACT-FILE` browse sequence (cbl:441–505) | Used only to derive the next transaction ID. The entire browse sequence is replaced by `TransactionIdGenerator.nextId()` (deviation #1). |
| `WS-CONF-PAY-FLG` / `CONF-PAY-YES` / `CONF-PAY-NO` COBOL flags (COBIL00C.cbl:51–53) | CICS pseudo-conversational state flags. HTTP request parameters (`confirmed=Y`) replace them. |

---

## Architecture Review Findings (not applied — PoC scope)

| # | Severity | Finding |
|---|---|---|
| M-1 | MEDIUM | `TransactionIdGenerator` uses `AtomicLong` initialized at startup. If multiple JVM instances run concurrently, each seeds its own counter from the current DB max and they will produce colliding IDs. For production, replace with a database SEQUENCE to guarantee global uniqueness across instances. |
| M-2 | MEDIUM | `BillPaymentService.pay()` performs two sequential writes (WRITE transaction, REWRITE account) without compensating rollback detection. If `accountRepository.save()` fails after `transactionRepository.save()` commits, the payment transaction is orphaned. The `@Transactional` boundary covers the H2 test database; for production, verify the datasource is XA-capable or accept the orphan risk. |
| M-3 | MEDIUM | `TransactionListController` restricts to ROLE_ADMIN (SEC-017) but its companion `TransactionDetailController` carries no `@PreAuthorize` annotation. Any authenticated user who knows a `tranId` can reach the detail view. Add `@PreAuthorize("hasRole('ADMIN')")` to `TransactionDetailController` until ownership guards are in place. |
| M-4 | LOW | `ReportTriggerController.trigger()` does not validate that `startDate` ≤ `endDate` or that dates are valid ISO-8601. CORPT00C validates month/day ranges and calls CSUTLDTC on custom dates. Add `LocalDate.parse()` + ordering check on the request body before the 202 is returned. |
| N-1 | Nit | `TransactionService.save()` calls `validate()` internally, meaning the controller also calls `validate()` in the step-1 handler. Double-validation is safe but the second invocation duplicates the XREF lookup. Acceptable for PoC; profile if XREF lookup becomes a bottleneck at scale. |

---

## Follow-ups

1. **User→account ownership model (SEC-017):** Both billing confirm/pay (`BillPaymentController`) and transaction list (`TransactionListController`) are ADMIN-only until `app_user` is linked to a customer ID. Required schema change: add `customer_id` to `app_user`, resolve via `CardXRefRepository`, assert `accountId` matches the authenticated principal's customer before allowing access.

2. **TransactionDetailController missing ownership guard (SEC-018):** `GET /transactions/{tranId}` has no role restriction and no ownership check. Any authenticated user can retrieve any transaction by guessing or enumerating IDs. Add `@PreAuthorize("hasRole('ADMIN')")` as a short-term fix, then implement principal-to-card ownership assertion when SEC-017 is resolved.

3. **AtomicLong → database SEQUENCE (RULE-012):** `TransactionIdGenerator` is safe within one JVM but breaks under horizontal scaling. Production requirement: use `CREATE SEQUENCE tran_id_seq` (PostgreSQL/H2 compatible) and call `NEXTVAL` via a native query. The 16-char zero-padded format must be preserved to remain compatible with any downstream system expecting `TRAN-ID PIC X(16)`.

4. **JobLauncher wiring for report trigger (Q10):** `ReportTriggerController` returns HTTP 202 with a UUID but does not actually start a batch job. Production wiring: inject `JobLauncher` + a `transactionReportJob` bean parameterized with `startDate`/`endDate`/`reportType`. The `jobExecutionId` in the response should be replaced with the Spring Batch `JobExecution.getId()` for status polling.

5. **COTRN00C paging not implemented:** `TransactionListController.list()` fetches all transactions for a card number in one query. For large card histories this will degrade. Implement Spring Data `Pageable` with prev/next navigation equivalent to COTRN00C's 10-row STARTBR/READNEXT pattern.
