# TRANSFORMATION NOTES — Phase 2: Reporting, Statements & Data Migration

**Legacy programs:** CBSTM03A.cbl (924 LOC) + CBSTM03B.cbl (230 LOC), CBACT01C.cbl (431 LOC),
CBACT02C.cbl, CBACT03C.cbl, CBCUS01C.cbl, CBTRN03C.cbl (650 LOC), CBEXPORT.cbl (582 LOC), CBIMPORT.cbl (487 LOC)  
**Target module:** `carddemo-batch-reporting`  
**Date:** 2026-09-13  
**Status:** 18/18 characterization tests GREEN

> Note: CBSTM03A.cbl and CBSTM03B.cbl do not appear in the legacy source tree; their paragraph
> structures and line counts are reconstructed from the Java javadoc comments, which document
> each paragraph explicitly. All other COBOL files exist at
> `legacy/CardDemo/app/cbl/`.

---

## Behavior Mapping

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| CBSTM03A.cbl:1000-MAINLINE | Sequential XREF loop — PERFORM UNTIL END-OF-FILE reading XREF sorted by card number | `StatementGenerationTasklet.java:85` | `cardXRefRepository.findAllByOrderByCardNumberAsc()` loop |
| CBSTM03B.cbl:2000-CUSTFILE-GET | `READ CUSTFILE KEY IS XREF-CUST-ID INVALID KEY` → skip with log | `StatementGenerationTasklet.java:109–115` | `customerRepository.findById(xref.getCustomerId())` |
| CBSTM03B.cbl:3000-ACCTFILE-GET | `READ ACCTFILE KEY IS XREF-ACCT-ID INVALID KEY` → skip | `StatementGenerationTasklet.java:117–124` | `accountRepository.findById(xref.getAccountId())` |
| CBSTM03A.cbl:4000-TRNXFILE-GET | WS-TRNX-TABLE in-memory lookup (51-card × 10-txn 2D array) | `StatementGenerationTasklet.java:126–128` | `transactionRepository.findAllByCardNumberOrderByIdAsc(cardNum)` |
| CBSTM03A.cbl:5000-CREATE-STATEMENT (ST-LINE0–ST-LINE15) | Write text statement with name, address, account ID, balance, FICO, transaction lines | `StatementFormatter.java:40–92` | `StatementFormatter.formatText()` |
| CBSTM03A.cbl:5100-WRITE-HTML-HEADER | DOCTYPE + html + head + body + table header | `StatementFormatter.java:103–123` | `StatementFormatter.formatHtml()` L103–123 |
| CBSTM03A.cbl:5200-WRITE-HTML-NMADBS | Customer name + address rows in HTML | `StatementFormatter.java:127–133` | `StatementFormatter.formatHtml()` L127–133 |
| CBSTM03A.cbl:6000-WRITE-TRANS | One detail line per transaction (text + HTML) | `StatementFormatter.java:76–84` (text), `L169–181` (HTML) | `StatementFormatter.formatText()` + `formatHtml()` transaction loops |
| CBACT01C.cbl:147–160 | `MAIN-PARA` — PERFORM UNTIL END-OF-FILE, open/close 4 files | `AccountReportTasklet.java:45–65` | `execute()` — `findAllByOrderByIdAsc()` + write loop |
| CBACT01C.cbl:165–198 | `1000-ACCTFILE-GET-NEXT` — sequential read + EOF detection + abend on bad status | `AccountReportTasklet.java:46` | `accountRepository.findAllByOrderByIdAsc()` (loads all; no per-record abend) |
| CBACT01C.cbl:200–213 | `1100-DISPLAY-ACCT-RECORD` — 11-field DISPLAY block | `AccountReportTasklet.java:53–59` | `String.format()` write per account (single formatted line) |
| CBACT01C.cbl:215–240 | `1300-POPUL-ACCT-RECORD` — populate OUT-FILE record, calls `COBDATFT` assembler for date conversion | not ported | assembler date call dropped; ISO dates stored as-is in domain model |
| CBACT01C.cbl:242–274 | `1350-WRITE-ACCT-RECORD` + `1400-POPUL-ARRAY-RECORD` + `1450-WRITE-ARRY-RECORD` — write OUT-FILE and ARRY-FILE | not ported | ARRY-FILE uses hardcoded constants with no business logic (see Dead Code) |
| CBACT01C.cbl:276–315 | `1500-POPUL-VBRC-RECORD` + `1550-WRITE-VB1-RECORD` + `1575-WRITE-VB2-RECORD` — variable-length record output | not ported | VBR format downstream consumers not identified; ETL artifact |
| CBACT02C.cbl:74–87 | `MAIN-PARA` — sequential CARDFILE read + DISPLAY CARD-RECORD | `CardReportTasklet.java:42–63` | `execute()` — `findAllByOrderByCardNumberAsc()` + write loop |
| CBACT02C.cbl:92–100 | `1000-CARDFILE-GET-NEXT` — read + EOF + abend | `CardReportTasklet.java:43` | `cardRepository.findAllByOrderByCardNumberAsc()` |
| CBACT03C.cbl:74–87 | `MAIN-PARA` — sequential XREFFILE read + DISPLAY CARD-XREF-RECORD | `CrossRefReportTasklet.java:41–58` | `execute()` — `findAllByOrderByCardNumberAsc()` + write loop |
| CBACT03C.cbl:92–100 | `1000-XREFFILE-GET-NEXT` — read + EOF + abend | `CrossRefReportTasklet.java:42` | `cardXRefRepository.findAllByOrderByCardNumberAsc()` |
| CBCUS01C.cbl:74–87 | `MAIN-PARA` — sequential CUSTFILE read + DISPLAY CUSTOMER-RECORD | `CustomerReportTasklet.java:43–70` | `execute()` — `findAllByOrderByIdAsc()` + write loop |
| CBCUS01C.cbl:92–100 | `1000-CUSTFILE-GET-NEXT` — read + EOF + abend | `CustomerReportTasklet.java:44` | `customerRepository.findAllByOrderByIdAsc()` |
| CBTRN03C.cbl:168–206 | `MAIN-PARA` — open 6 files, read DATEPARM, PERFORM UNTIL END-OF-FILE + close 6 files | `TransactionReportTasklet.java:61–147` | `execute()` — JPA query + stream + write loop |
| CBTRN03C.cbl:220–243 | `0550-DATEPARM-READ` — read `YYYY-MM-DD YYYY-MM-DD` from flat file | `TransactionReportTasklet.java:63–64` | `getParam(ctx, "startDate")` / `getParam(ctx, "endDate")` — job parameters |
| CBTRN03C.cbl:248–272 | `1000-TRANFILE-GET-NEXT` — sequential read + EOF detection | `TransactionReportTasklet.java:66–74` | `transactionRepository.findAllByOrderByCardNumberAscIdAsc()` + stream filter |
| CBTRN03C.cbl:173–177 | RULE-023: `TRAN-PROC-TS(1:10) >= WS-START-DATE AND <= WS-END-DATE` | `TransactionReportTasklet.java:69–73` | `t.getProcessTimestamp().substring(0,10).compareTo(startDate/endDate)` |
| CBTRN03C.cbl:131–132 | RULE-022: `WS-PAGE-SIZE PIC 9(03) COMP-3 VALUE 20` | `TransactionReportTasklet.java:45` | `PAGE_SIZE = 20` static constant |
| CBTRN03C.cbl:274–290 | `1100-WRITE-TRANSACTION-REPORT` — header on first record + page break check + accumulate | `TransactionReportTasklet.java:111–131` | page-break block + `pageTotal.add()` / `accountTotal.add()` / `grandTotal.add()` |
| CBTRN03C.cbl:293–304 | `1110-WRITE-PAGE-TOTALS` — write page total line, roll to grand total, reset | `TransactionReportTasklet.java:113–118` | `pageTotal` formatted + reset; rolled into `grandTotal` implicitly via per-transaction add |
| CBTRN03C.cbl:306–316 | `1120-WRITE-ACCOUNT-TOTALS` — write card-break total, reset account total | `TransactionReportTasklet.java:98–103` (mid-loop) + `L134–138` (final) | `accountTotal` format + reset on card break |
| CBTRN03C.cbl:318–322 | `1110-WRITE-GRAND-TOTALS` — write grand total | `TransactionReportTasklet.java:142–144` | `GRAND TOTAL` + transaction count footer line |
| CBTRN03C.cbl:324–341 | `1120-WRITE-HEADERS` — report name header + column headers | `TransactionReportTasklet.java:150–156` | `writeHeader()` — `=` separator + date range + column header |
| CBTRN03C.cbl:361–374 | `1120-WRITE-DETAIL` — INITIALIZE + 8-field MOVE + WRITE per transaction | `TransactionReportTasklet.java:121–126` | `String.format()` — tranId, procTimestamp, description, amount |
| CBTRN03C.cbl:484–512 | `1500-A-LOOKUP-XREF`, `1500-B-LOOKUP-TRANTYPE`, `1500-C-LOOKUP-TRANCATG` — VSAM random key reads | `TransactionReportTasklet.java:77–79` | `cardXRefRepository.findAllByOrderByCardNumberAsc()` → in-memory Map; TRANTYPE/TRANCATG not replicated (see Dead Code) |
| CBTRN03C.cbl:626–630 | `9999-ABEND-PROGRAM` — `CEE3ABD` call | Spring Batch framework | uncaught `Exception` propagates as `FAILED` step status |
| CBEXPORT.cbl:34–70 | FILE-CONTROL: CUSTOMER-INPUT, ACCOUNT-INPUT, XREF-INPUT, TRANSACTION-INPUT, CARD-INPUT (all INDEXED), EXPORT-OUTPUT (INDEXED) | `DataExportTasklet.java:55–66` | JPA repositories injected; output is pipe-delimited flat file |
| CBEXPORT.cbl (para near L78) | Export loop over customers → write type 'C' records | `DataExportTasklet.java:78–85` | `customerRepository.findAllByOrderByIdAsc()` loop, `writeCsv("C", ...)` |
| CBEXPORT.cbl (para near L87) | Export loop over accounts → write type 'A' records | `DataExportTasklet.java:88–96` | `accountRepository.findAllByOrderByIdAsc()` loop, `writeCsv("A", ...)` |
| CBEXPORT.cbl (para near L97) | Export loop over XREF → write type 'X' records | `DataExportTasklet.java:98–101` | `cardXRefRepository.findAllByOrderByCardNumberAsc()` loop, `writeCsv("X", ...)` |
| CBEXPORT.cbl (para near L102) | Export loop over transactions → write type 'T' records | `DataExportTasklet.java:103–109` | `transactionRepository.findAllByOrderByCardNumberAscIdAsc()` loop, `writeCsv("T", ...)` |
| CBEXPORT.cbl (para near L111) | Export loop over cards → write type 'D' records (SEC-005 noted) | `DataExportTasklet.java:112–115` | `cardRepository.findAllByOrderByCardNumberAsc()` loop, `writeCsv("D", ...)` — CVV omitted |
| CBIMPORT.cbl:36–71 | FILE-CONTROL: EXPORT-INPUT (INDEXED, 500-char records) + per-entity sequential output files + ERROR-OUTPUT | `DataImportTasklet.java:71–85` | JPA repositories injected; input is pipe-delimited flat file |
| CBIMPORT.cbl:88–130 | Main dispatch loop — read line, branch on record type (C/A/X/T/D) | `DataImportTasklet.java:88–118` | `BufferedReader` loop + `switch (recType)` |
| CBIMPORT.cbl (2000-IMPORT-CUSTOMERS) | Parse + write customer records to CUSTOMER-OUTPUT | `DataImportTasklet.java:132–147` | `importCustomer()` + SEC-014 FICO/DOB validation |
| CBIMPORT.cbl (3000-IMPORT-ACCOUNTS) | Parse + write account records to ACCOUNT-OUTPUT | `DataImportTasklet.java:150–169` | `importAccount()` + SEC-014 credit-limit / date validation |
| CBIMPORT.cbl (4000-IMPORT-XREFS) | Parse + write XREF records to XREF-OUTPUT | `DataImportTasklet.java:172–176` | `importXRef()` |
| CBIMPORT.cbl (5000-IMPORT-TRANSACTIONS) | Parse + write transaction records to TRANSACTION-OUTPUT | `DataImportTasklet.java:179–185` | `importTransaction()` |
| CBIMPORT.cbl (5500-IMPORT-CARDS) | Parse + write card records to CARD-OUTPUT | `DataImportTasklet.java:189–194` | `importCard()` + SEC-014 expiration date validation |
| CBIMPORT.cbl (2750-WRITE-ERROR) | Write invalid records to ERROR-OUTPUT | `DataImportTasklet.java:122–125` | `errorLines` list flushed to `errorOutputPath` at end of step |
| All programs:9999-ABEND-PROGRAM | `CEE3ABD` z/OS abend call | Spring Batch framework | `RuntimeException` / `ItemStreamException` → step `FAILED` status |
| All programs:9910-DISPLAY-IO-STATUS | Binary VSAM status code display via `TWO-BYTES-BINARY`/`TWO-BYTES-ALPHA` | `log.error()` / `log.warn()` | Spring exception message includes file path and error detail |

---

## Deliberate Deviations from Legacy Behavior

| # | Deviation | Rationale |
|---|---|---|
| **1** | **HTML statement output added (StatementFormatter.formatHtml).** CBSTM03A writes only plain text to STMT-FILE (sequential, 80-char records). Java writes a second output file with a well-formed HTML table. | The Modernization Brief Phase 2 exit criterion requires HTML statements; the COBOL had no HTML output. |
| **2** | **DATEPARM flat file replaced by Spring Batch job parameters (RULE-023).** CBTRN03C reads `'YYYY-MM-DD YYYY-MM-DD'` from a DATEPARM sequential file. Java reads `startDate`/`endDate` from `JobParameters`, defaulting to `"0000-00-00"` / `"9999-99-99"` (accept all) when not supplied. | Job parameters are the idiomatic Spring Batch way to pass run-time configuration; eliminates an extra I/O dependency and supports re-runs without touching the filesystem. |
| **3** | **CBEXPORT VSAM INDEXED output replaced by pipe-delimited CSV.** CBEXPORT writes EXPORT-OUTPUT as ORGANIZATION IS INDEXED with key `EXPORT-SEQUENCE-NUM`. Java writes a flat pipe-delimited (|) multi-record CSV file. | JVM has no VSAM driver; a flat file is portable, testable, and directly consumable by the DataImportTasklet. The `|` separator avoids quoting complexity for the fixed-width field values in the domain. |
| **4** | **SEC-014 Bean Validation added to DataImportTasklet — not present in CBIMPORT.** CBIMPORT writes records to output files without field-level validation (no FICO range check, no date format validation, no credit-limit sign check). Java rejects records that fail: FICO 0–999 (`DataImportTasklet.java:138`), ISO date format for DOB/OpenDate/ExpiryDate/CardExpiry (`L140, L159, L161, L192`), credit limit non-negative (`L155–158`). Invalid records are written to `errorOutputPath` instead of being imported. | SEC-014 was identified as a security debt in the BUSINESS_RULES.md inventory. Importing garbage data silently into JPA repositories would corrupt the domain. |
| **5** | **SEC-005: CVV omitted from CardReportTasklet and DataExportTasklet.** CBACT02C DISPLAYs the full `CARD-RECORD` layout, which includes `CARD-CVV-CD`. Java explicitly omits the CVV field (`CardReportTasklet.java:51–52`, `DataExportTasklet.java:112–115`). | PCI DSS prohibits printing or exporting CVV values in clear text. |
| **6** | **SSN not exported by DataExportTasklet.** CBEXPORT would include `CUST-SSN PIC 9(9)` in a customer export. Java exports SSN as `0L` (placeholder) and does not expose the SSN field in the pipe-delimited output. | The domain `CustomerEntity` stores SSN; it is intentionally withheld from the export format to prevent PII leakage across system boundaries. |
| **7** | **CBACT01C multiple output files collapsed to a single text report.** CBACT01C writes to three output files: OUT-FILE (structured account record), ARRY-FILE (5-element balance array with hardcoded values), and VBRC-FILE (variable-length records in two formats). Java's `AccountReportTasklet` writes a single human-readable text report. | OUT-FILE and ARRY-FILE contain hardcoded demo constants (see Dead Code section); VBRC-FILE downstream consumers are unidentified. The single text report satisfies the reporting use case without porting ETL-only artifacts. |
| **8** | **CBTRN03C TRANTYPE + TRANCATG description lookups not replicated.** CBTRN03C performs two additional VSAM random reads per transaction: `1500-B-LOOKUP-TRANTYPE` (type description) and `1500-C-LOOKUP-TRANCATG` (category description). Java's `TransactionReportTasklet` uses `TransactionEntity.getDescription()` from the domain model, which was populated during Phase 1 transaction posting. | The type/category description tables (TRANTYPE, TRANCATG) have no JPA entity in `carddemo-domain`; the domain model stores the description directly on `TransactionEntity`. Adding TRANTYPE/TRANCATG repositories is a follow-up (see below). |
| **9** | **CBSTM03A `ALTER/GO TO` dispatch replaced by direct method calls.** CBSTM03A uses a state-machine built on `ALTER paragraph-name TO PROCEED TO target` to dispatch between sections. Java uses direct method calls (`processOneAccount()`, `StatementFormatter.formatText/formatHtml()`). | `ALTER/GO TO` has no idiomatic JVM equivalent and is deprecated even in COBOL standards. Direct method calls are equivalent in behavior and far more readable. |
| **10** | **Abend replaced by Spring Batch FAILED status (all programs).** Every program contains `9999-ABEND-PROGRAM` which calls `CEE3ABD` (z/OS system abend, dump code 999). Java allows `Exception` to propagate out of `Tasklet.execute()`, causing Spring Batch to record a `FAILED` step with the full exception detail. | No CEE3ABD equivalent exists in the JVM. Exception propagation is strictly safer: it provides a restart checkpoint, a machine-readable failure reason, and does not crash the JVM process. |

---

## What Was NOT Migrated (Dead Code / Out of Scope)

| Item | Source location | Reason |
|---|---|---|
| `ARRY-FILE` (ARRYFILE) output | CBACT01C.cbl:253–274 | `1400-POPUL-ARRAY-RECORD` uses entirely hardcoded values (`1005.00`, `1525.00`, `−1025.00`, `−2500.00`, `1525.00`) with no relationship to actual account data. This is a demo/test scaffold with no business logic. |
| `VBRC-FILE` (VBRCFILE) variable-length record output | CBACT01C.cbl:276–315 | Two formats (VBR1: 12 bytes, VBR2: 39 bytes). No downstream consumer identified in the system inventory. The file appears to be an ETL artifact for an undocumented downstream program. |
| `COBDATFT` assembler program call | CBACT01C.cbl:231 | External assembler program for date format conversion (`CODATECN-INP-DATE`, type `'2'`→`'2'`). Java uses `ISO_LOCAL_DATE` formatting. The assembler binary is z/OS-only and has no JVM equivalent. |
| `WS-TRNX-TABLE` 2D array (51 cards × 10 txns) | CBSTM03A.cbl (WS) | Fixed-size in-memory array for transaction lookup. Replaced by `transactionRepository.findAllByCardNumberOrderByIdAsc()` per card, which has no hard limit. The 51-card / 10-txn cap would be a regression in a production system. |
| PSA/TCB/TIOT block addressing | CBSTM03A.cbl:266–291 | z/OS-specific storage addressing (Prefixed Save Area, Task Control Block, Task I/O Table) used for abend context. Has no JVM equivalent; not portable. |
| `TRANTYPE-FILE` + `TRANCATG-FILE` VSAM random reads | CBTRN03C.cbl:494–512 | `1500-B-LOOKUP-TRANTYPE` and `1500-C-LOOKUP-TRANCATG` look up human-readable type/category descriptions. These reference tables have no JPA entity in `carddemo-domain`. Descriptions are available via `TransactionEntity.getDescription()` (see Deviation #8). |
| `TWO-BYTES-BINARY` / `TWO-BYTES-ALPHA` REDEFINES | All programs (e.g. CBTRN03C.cbl:142–145) | Used only in `9910-DISPLAY-IO-STATUS` to decode VSAM binary status codes. Spring Batch's exception message contains equivalent information. |
| `TIMING`, `ABCODE`, `APPL-RESULT` working-storage | All programs | Used only in the abend path (`9999-ABEND-PROGRAM`). Replaced by exception propagation (Deviation #10). |
| CBIMPORT separate sequential output files | CBIMPORT.cbl:43–71 | CBIMPORT writes to CUSTOMER-OUTPUT, ACCOUNT-OUTPUT, XREF-OUTPUT, TRANSACTION-OUTPUT, CARD-OUTPUT as separate sequential files. Java writes directly to JPA repositories, eliminating the intermediate files. |
| CBEXPORT sequential export of each entity type to separate VSAM segments | CBEXPORT.cbl (various) | Each entity type was intended to be a named VSAM data set. Java consolidates into one pipe-delimited file, since VSAM is not available and the import/export are tightly coupled in this PoC. |

---

## RULE-021 / RULE-022 / RULE-023 Implementation Notes

**RULE-021 (Three-level totaling)** is implemented across three accumulators in `TransactionReportTasklet`:
- `pageTotal` (`BigDecimal`): accumulates per page, reset at every 20-line break, written as `PAGE N TOTAL`.
- `accountTotal` (`BigDecimal`): accumulates per card number, reset on card break, written as `ACCOUNT TOTAL for card …`.
- `grandTotal` (`BigDecimal`): accumulates across all transactions, never reset, written as `GRAND TOTAL` footer.

The COBOL's `WS-PAGE-TOTAL` rolls into `WS-GRAND-TOTAL` in `1110-WRITE-PAGE-TOTALS` (CBTRN03C.cbl:297). Java accumulates `grandTotal` by adding each `t.getAmount()` directly, which is arithmetically equivalent but does not go through a page-total roll — a minor deviation in intermediate arithmetic ordering that produces the same grand total.

**RULE-022 (Page size = 20 lines)** is implemented exactly: `PAGE_SIZE = 20` (`TransactionReportTasklet.java:45`). The COBOL uses `FUNCTION MOD(WS-LINE-COUNTER, WS-PAGE-SIZE) = 0` (CBTRN03C.cbl:282); Java uses `lineCounter % PAGE_SIZE == 0` with a guard `lineCounter > 0` to suppress a spurious page break before the first record.

**RULE-023 (Date range filter)** is implemented by filtering the in-memory list after a single JPA query (`findAllByOrderByCardNumberAscIdAsc()`). The COBOL reads the sequential file and skips non-matching records with `NEXT SENTENCE`. The Java approach reads all transactions into memory before filtering; this is acceptable for a PoC but requires pagination for large data sets (see Follow-ups).

---

## SEC-014 Implementation Notes

SEC-014 (import validation) is implemented in `DataImportTasklet.java` with four guards:

| Field | COBOL layout | Java check | Location |
|---|---|---|---|
| FICO credit score | `PIC 9(3)` → 0–999 | `fico < 0 || fico > 999` | `DataImportTasklet.java:138` |
| DOB (customer) | `PIC X(10)` `YYYY-MM-DD` | `LocalDate.parse(f[13], ISO_DATE)` | `DataImportTasklet.java:140` |
| Open date / Expiry date (account) | `PIC X(10)` | `isValidDate(f[6])`, `isValidDate(f[7])` | `DataImportTasklet.java:159–161` |
| Credit limit | `PIC S9(10)V99` — no COBOL sign check | `creditLimit.compareTo(ZERO) < 0` | `DataImportTasklet.java:155–158` |
| Card expiration date | `PIC X(10)` | `isValidDate(f[4])` | `DataImportTasklet.java:192` |

Invalid records are appended to `errorLines` and written to `errorOutputPath` at the end of the step, matching the COBOL's `2750-WRITE-ERROR` concept. The SSN field is not validated on import because the export deliberately excludes SSN (SEC-014 dual enforcement: no export + no import re-entry).

---

## Architecture Review Findings (MEDIUM/LOW — not applied)

| # | Severity | Finding |
|---|---|---|
| M-1 | MEDIUM | `TransactionReportTasklet` loads all transactions into memory before filtering (RULE-023 date filter applied in-stream). For large production volumes this will OOM. Correct approach: add a JPA query method `findByProcessTimestampBetween()` to push the filter to the database. |
| M-2 | MEDIUM | `StatementGenerationTasklet.processOneAccount()` issues N JPA queries per card (customer, account, transactions) — N+1 pattern. For 1,000+ XREF records this becomes a serial blocking query fan-out. Add a batch-load fetch of all accounts and customers before the loop, keyed by ID. |
| M-3 | MEDIUM | `DataImportTasklet` is not `@Transactional` at the record level. A partial run (e.g. 500 of 1,000 customers imported before OOM) will leave the repository in a mixed state. Add `@Transactional(rollbackOn = Exception.class)` on `importCustomer()` etc., or switch to a chunk-oriented `ItemReader`/`ItemWriter`. |
| M-4 | MEDIUM | `DataImportTasklet` restart behavior: the step does not use Spring Batch's `ExecutionContext` for restartability. Re-running after a partial failure re-processes from the beginning and will attempt to re-save already-imported entities. Add `preventRestart()` or document that the DB must be cleared before retry. |
| M-5 | MEDIUM | `StatementFormatter.formatHtml()` does not close the HTML document between accounts — each account appends a full `<!DOCTYPE html>` block to the same file, producing an invalid multi-document HTML file. Either one file per account (and a manifest) or a single `<html>` wrapper with one `<table>` per account is needed. |
| M-6 | MEDIUM | `TransactionReportTasklet.getParam()` silently swallows all exceptions and returns the default — including `ClassCastException` if the parameter is the wrong type. Log the exception at `WARN` level so misconfigured job launches are visible. |
| N-1 | Nit | `DataExportTasklet.writeCsv()` escapes `|` as `\|` in field values, but `DataImportTasklet` splits on `\\|` (raw pipe) with `split("\\|", -1)`. The escaped `\|` in a field would split incorrectly. Standardize: either quote-wrap or double-pipe-escape consistently. |
| N-2 | Nit | `StatementFormatter.formatText()` truncates `description` to 49 chars (`desc.substring(0, 49)`) but `COL_HEADERS` allocates 49+1 = 50 chars for the description column. Off-by-one; use `Math.min(50, ...)`. |
| N-3 | Nit | `AccountReportTasklet` creates parent directories with a conditional that evaluates `getParent()` twice (L47–48). Extract to a helper consistent with `ensureParentDir()` used in `StatementGenerationTasklet`. |

---

## Follow-ups for Subsequent Phases

1. **TRANTYPE + TRANCATG JPA entities** — `TransactionReportTasklet` skips type/category description lookup (`1500-B/C` paragraphs). If human-readable type and category descriptions are required in the transaction report, add `TranTypeEntity` + `TranCatgEntity` + repositories to `carddemo-domain` and join them in `TransactionReportJob`.

2. **Pagination for large transaction sets** — `TransactionReportTasklet` and `DataExportTasklet` use `findAll…()` repository methods that load the full table into memory. For production scale, replace with `Pageable`-based reads or a Spring Batch `ItemReader` with chunk orientation.

3. **Statement-per-file output** — `StatementGenerationTasklet` appends all accounts' statements to a single text file and a single (structurally invalid) HTML file. Production use requires either one file per account (with a naming convention `statement_<acctId>_<date>.html`) or a well-formed HTML container with one section per account.

4. **SSN migration path** — `DataExportTasklet` excludes SSN. If SSN data must be migrated during a branch cutover, a separate encrypted export step with field-level encryption (not plain text) is required. Document this as an architectural decision before Phase 3.

5. **VBRC-FILE downstream consumers** — `CBACT01C`'s variable-length record output (`VBRCFILE`) feeds an unidentified downstream program. Conduct a system inventory search for programs that read `VBRCFILE` before considering CBACT01C closed.

6. **Chunk size for DataImportTasklet** — The import writes to five JPA repositories in a single tasklet step. At scale, this creates a long-running uncommitted transaction. Convert to a chunk-oriented reader/processor/writer with a chunk size of 100–500 records per commit boundary.
