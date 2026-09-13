# CardDemo — Modernization Assessment

**Generated:** 2026-09-13  
**Migration goal:** Exploratory COBOL → Java proof-of-concept (see `PREFLIGHT.md` Premises P1–P7)  
**Tool:** `scc` 4.1.0 (all metrics), `cloc` 2.08 (secondary), GnuCOBOL 3.2.0 (syntax probe)

---

## Executive Summary

CardDemo is a mainframe COBOL credit-card management demo system comprising 106 COBOL programs/copybooks and 55 JCL batch jobs (33,876 COBOL LOC), built on IBM Enterprise COBOL v6.3, CICS/BMS online screens, VSAM key-sequenced data stores, and z/OS JES2 batch — with optional DB2, IMS DB, and MQ extension modules. The system is structurally decomposable into 10 functional domains, but a single god-program (`COACTUPC`, 4,236 LOC, cyclomatic complexity 122) concentrates presentation, validation, and persistence logic for the account-update flow. Three critical security findings — plaintext CVV storage, plaintext FTP credentials in version control, and plaintext user passwords in both JCL seed data and VSAM storage — must not be carried into the Java reimplementation under any circumstances. The recommended migration pattern is **Rearchitect** (cross-stack COBOL→Java/Spring Boot), beginning with the 13 batch programs that have no CICS dependency, using Spring Batch for EOD processing and Spring MVC for online screens; the command is `/modernize-transform`.

---

## System Inventory

### scc Output (full system)

```
Language            Files       Lines    Blanks  Comments       Code  Complexity
───────────────────────────────────────────────────────────────────────────────
COBOL                 106      40,826     2,706     4,244     33,876        154
JCL                    55       3,388        22     1,570      1,796        398
Shell                   9         408        82        37        289         49
Plain Text             10       4,582         0         0      4,582          0
Markdown                6       1,112       270         0        842          0
PL/SQL                  6         588         0       580          8          0
SQL                     6          53         0         0         53          0
Assembly                2         114         0         0        114          2
AWK                     1           5         0         1          4          0
───────────────────────────────────────────────────────────────────────────────
Total (code)                                                  41,724        603
```

### Technology Fingerprint

| Dimension | Detail |
|---|---|
| Primary language | IBM Enterprise COBOL (fixed-column, APOST mode, IBM-strict dialect) |
| Online subsystem | IBM CICS/BMS — 17 BMS map files, EXEC CICS XCTL/READ/WRITE/SYNCPOINT |
| Batch subsystem | z/OS JES2 — 55 JCL jobs, 6 JCL procs, CA7 + Control-M schedulers |
| Build compiler | IBM IGYCRCTL v6.3 (`IGY.SIGYCOMP.V63`) via JES2 batch (Ensono managed mainframe) |
| Primary data store | VSAM KSDS: ACCTDATA, CUSTFILE, CARDFILE, CARDXREF, USRSEC, TRANSACT, TCATBALF |
| Sequential data | VSAM ESDS / PS: DALYTRAN, DISCGRP; GDG: DALYREJS, SYSTRAN statements |
| Optional DB2 | `app-transaction-type-db2/` — transaction type master; parameterized host variables only |
| Optional IMS DB | `app-authorization-ims-db2-mq/` — pending auth records; DBD/PSB present |
| Optional MQ | Both optional modules — inbound authorization requests |
| Integration | CICS internal reader TDQ (online→batch job submission); FTP (data transfer to Ensono) |
| Screen layer | BMS map copybooks (17 maps) + CICS COMMAREA (`COCOM01Y` — universal session state) |
| Runtime utilities | IBM Language Environment `CEEDAYS` (date); `CEE3ABD` (abend); MVS `STIMER` wrapper |
| Test suite | **None** — no test framework, no characterization tests, no JCL test harness |

### Top-Complexity Files (scc, COBOL only)

| File | LOC | Complexity | Role |
|---|---|---|---|
| `app/cbl/COACTUPC.cbl` | 3,368 | **122** | Account update (god-program — see TD-01) |
| `app/cbl/COCRDLIC.cbl` | 1,093 | — | Credit card list online |
| `app/cbl/COCRDUPC.cbl` | 1,195 | — | Credit card update online |
| `app/cbl/COACTVWC.cbl` | 703 | — | Account view online |
| `app/cbl/COTRN02C.cbl` | 614 | — | Transaction add online |
| `app/cbl/CBSTM03A.CBL` | 784 | — | Statement generator (calls CBSTM03B 13×) |
| `app/cbl/CBTRN02C.cbl` | 619 | — | Batch transaction post |

---

## Architecture at a Glance

Diagram: `analysis/CardDemo/ARCHITECTURE.mmd` (render with `mmdc` or paste into mermaid.live)

| Domain | Programs | Key Copybooks / Data | Role |
|---|---|---|---|
| **D1 — Security/Signon** | COSGN00C, COUSR00-03C | CSUSR01Y (SEC-USER-DATA 80B) | Login, user CRUD against USRSEC KSDS; XCTL to D2 |
| **D2 — Navigation Shell** | COMEN01C, COADM01C | COCOM01Y (COMMAREA session state), COMEN02Y (11 opts), COADM02Y (6 opts) | Data-driven menu dispatch to all other domains via XCTL |
| **D3 — Account & Customer (Online)** | COACTVWC, COACTUPC | CVACT01Y (ACCOUNT-RECORD 300B), CVCUS01Y (CUSTOMER-RECORD 500B), CVACT02-3Y | VSAM ACCTDATA + CUSTFILE read/rewrite; 2-file atomic update |
| **D4 — Credit Card (Online)** | COCRDLIC, COCRDSLC, COCRDUPC | CVCRD01Y, CVACT01-3Y, CVCUS01Y | Card list/view/update; scope-enforced: non-admin sees only their cards |
| **D5 — Transaction (Online)** | COTRN00-02C, COBIL00C, CORPT00C | CVTRA05Y (TRAN-RECORD 350B) | Tran list/view/add, bill payment, report trigger via CICS TDQ internal reader |
| **D6 — Batch TXN Processing (EOD)** | CBTRN01C, CBTRN02C, CBACT04C | CVTRA06Y (DALYTRAN-RECORD), CVTRA01-2Y | Validate daily txns → post → interest calculation; CA7 trigger chain |
| **D7 — Reporting & Statements** | CBACT01-03C, CBCUS01C, CBSTM03A+B, CBTRN03C | COSTM01, CVTRA03-4Y, CVTRA07Y | File reports, text+HTML account statements, transaction detail report |
| **D8 — Data Migration** | CBEXPORT, CBIMPORT | CVEXPORT (500B multi-type with REDEFINES) | Multi-file VSAM → sequential export and reverse import for branch migration |
| **D9 — Optional Extensions** | auth-ims-db2-mq, txtype-db2, vsam-mq modules | IMS DBD/PSB, DB2 DDL/DCL | Feature-gated; menu slots wired in base COMMAREA tables (PGMIDERR if absent) |
| **D10 — Platform Utilities** | CSUTLDTC, COBSWAIT, COBDATFT (ASM), MVSWAIT (ASM) | CSUTLDPY, CSSETATY, CSMSG01Y, CSSTRPFY | Date arithmetic (LE CEEDAYS), screen attribute tables, string validation, wait |

**Dangling references of note:**
- `COMEN02Y.cpy:89` hardcodes `COPAUS0C` (auth extension) — COMEN01C has **no PGMIDERR handler**; selecting menu option 11 without the extension installed will **abend the CICS task**.
- `UNUSED1Y.cpy` — dead copybook, never referenced, safe to delete.
- `COUSR01C.cbl` references `DFHATTR` (IBM CICS system copybook, absent from tree) — build-time dependency on CICS SDFHCOB library.

---

## Production Runtime Profile

**No telemetry available.** No APM MCP server is connected; no batch job logs or runtime exports were supplied.

The following jobs are the highest-priority candidates for timing instrumentation before migration, based on static analysis of data volumes and processing complexity:

| Job/Program | Domain | Estimated Sensitivity | Why |
|---|---|---|---|
| `jcl/POSTTRAN.jcl` (CBTRN02C) | D6 | High | Posts all daily transactions to ACCTDATA; holds VSAM file exclusive during run |
| `jcl/INTCALC.jcl` (CBACT04C) | D6 | High | Reads DISCGRP × TCATBALF for interest calc; GDG output feeds downstream |
| `jcl/CREASTMT.JCL` (CBSTM03A+B) | D7 | Medium | 13-call PERFORM chain per account; scales with customer count |
| `jcl/CBEXPORT.jcl` (CBEXPORT) | D8 | Medium | Full sequential scan of 5 VSAM files |

Obtain production job logs from the CA7/Control-M scheduler before the Java equivalence tests are designed.

---

## Technical Debt

Top 10 findings, ranked by remediation value for the Java migration.

| Rank | ID | Finding | File:Line | Java Migration Impact |
|---|---|---|---|---|
| 1 | TD-01 | **God-program: COACTUPC** — 4,236 LOC, CCN 122, 51 GO TO statements. Mixes screen I/O, validation (8 field types), VSAM persistence, and screen attribute expansion (39× COPY CSSETATY REPLACING) in one compilation unit. | `app/cbl/COACTUPC.cbl:1` (entire file) | Cannot extract or test any layer independently. Must decompose into `ScreenRenderer`, `AccountUpdateValidator`, `AccountRepository` + `CustomerRepository`, `AccountUpdateController` before characterization tests are meaningful. |
| 2 | TD-07 | **Asymmetric rollback** — ACCT REWRITE failure at line 4077 exits without `SYNCPOINT ROLLBACK`; CUST REWRITE failure at line 4101 correctly issues ROLLBACK. Current z/OS behavior is correct (task-end releases locks), but naïve Java translation produces a `@Transactional` method that does not rollback on account-write failure. | `app/cbl/COACTUPC.cbl:4076-4102` | Financial data integrity defect if copied as-is to Java. Both `.save()` calls must be in one `@Transactional` method with Spring exception propagation. |
| 3 | TD-09 | **GO TO spaghetti** — phone/SSN validation in COACTUPC uses paragraph fall-through plus 51 GO TO statements. Nominal (non-error) path runs all four sub-paragraphs in sequence without a PERFORM; error paths short-circuit via GO TO. | `app/cbl/COACTUPC.cbl:2225-2424` | Migration team may silently break the nominal path by treating each paragraph as a separate Java method without the fall-through. Must be restructured as `PhoneValidator.validate()` with early returns. |
| 4 | TD-04 | **Copy-paste duplication** — VSAM I/O paragraphs `9200-GETCARDXREF-BYACCT`, `9300-GETACCTDATA-BYACCT`, `9400-GETCUSTDATA-BYCUST` repeated identically in COACTUPC and COACTVWC. `9910-DISPLAY-IO-STATUS` verbatim in 8 batch programs. | `app/cbl/COACTVWC.cbl:723` vs `app/cbl/COACTUPC.cbl:3650` | Independent defect surfaces — one change must be applied 8 times. In Java: one shared `CardXRefRepository.findByAccountId()` + `BatchIOStatusHandler`. |
| 5 | TD-06 | **63 hardcoded DSNs, 14 hardcoded VOLSERs** — All DD statements use fully qualified `AWS.M2.CARDDEMO.*` DSNs. No symbolic HLQ variable. CICS region `CICSAWSA` hardcoded in 4 JCL files. | `app/jcl/CARDFILE.jcl:52` (VOLSER), `app/jcl/CARDFILE.jcl:26` (CICS region) | The DSN→table-name mapping for the Java/AWS data tier must be built from these 63 values. Parameterize with `&HLQ` before migration inventory freeze. |
| 6 | TD-05 | **Missing COND= in 6 multi-step JCL jobs** — CARDFILE (8 steps), ESDSRRDS (6), DUSRSECJ (4), COMBTRAN, TRANIDX, XREFFILE: no step dependencies documented. Later steps run even if earlier DEFINE CLUSTER failed. | `app/jcl/CARDFILE.jcl` (all steps) | Step dependencies must be inferred from IDCAMS semantics to build correct Step Functions / AWS Batch DAG. Inference is risky. Add `COND=(0,LT)` before migration. |
| 7 | TD-03 | **Plaintext credentials in JCL** — FTP credentials and all 10 USRSEC seed passwords embedded as in-stream JCL data in version-controlled files. | `app/jcl/FTPJCL.JCL:33-35`, `app/jcl/ESDSRRDS.jcl:36-45` | Must not be carried into Java config files. Use AWS Secrets Manager + Spring `@Value` injection. See SECRETS.local.md. |
| 8 | TD-10 | **CODING-TO-BE-DONE sentinel in 4 programs** — `88 CODING-TO-BE-DONE VALUE 'Looks Good.... so far'` declared but never SET or tested in COACTUPC, COCRDUPC, COACTVWC, COCRDSLC. | `app/cbl/COACTUPC.cbl:527`, `app/cbl/COCRDUPC.cbl:213`, `app/cbl/COACTVWC.cbl:137`, `app/cbl/COCRDSLC.cbl:157` | Unknown functionality gap in the 4 core read/write flows. Requires SME clarification before migration; may represent missing business requirements in the Java backlog. |
| 9 | TD-08 | **Dead feature: SEND-LONG-TEXT / WS-LONG-MSG** — `WS-LONG-MSG PIC X(500)` declared but never populated; `PERFORM SEND-LONG-TEXT` commented out in 3 error branches; paragraph never defined. | `app/cbl/COACTUPC.cbl:462, 3694, 3744, 3793` | Error branches for rare VSAM failures will produce only a truncated 78-char message in production. Java migration needs `@ControllerAdvice` with structured logging of CICS RESP/RESP2 codes. |
| 10 | TD-02 | **Dead copybook: UNUSED1Y.cpy** — Defines `01 UNUSED-DATA` mirroring CSUSR01Y field shapes. Zero references across all 31 programs. | `app/cpy/UNUSED1Y.cpy:1-11` | False inventory item. Will produce a Java class with no callers. Delete after SME confirms it is not compiled into any separately-linked load module. |

---

## Security Findings

**Credential inventory in SECRETS.local.md (gitignored — not for sharing).**

| ID | CWE | Severity | File:Line | Description | Recommendation |
|---|---|---|---|---|---|
| SEC-001 | CWE-259 | **Critical** | `app/jcl/FTPJCL.JCL:33-35` | FTP username + password (`c***r` / `f****1`) in plaintext in-stream JCL committed to git. Grants access to mainframe FTP server handling cardholder datasets. | Rotate immediately (treat as compromised). Replace with RACF PassTicket or SFTP key. Scrub git history. |
| SEC-002 | CWE-259 | **Critical** | `app/jcl/ESDSRRDS.jcl:36-45`, `DUSRSECJ.jcl:35-44` | Plaintext passwords for all 10 USRSEC accounts (5 admin, 5 user) in version-controlled JCL in-stream data. | Rotate all accounts. Bootstrap via secrets manager. Scrub git history. |
| SEC-005 | CWE-312 + PCI DSS 3.2.1 | **Critical** | `app/cpy/CVACT02Y.cpy:7` | `CARD-CVV-CD PIC 9(03)` stored in CARDDATA VSAM KSDS. PCI DSS 3.2.1 prohibits CVV storage after authorization. All JCL with DISP=SHR on CARDDATA can read CVV for all cards. | Remove `CARD-CVV-CD` field entirely. Do not include CVV in the Java data model. Purge existing VSAM data. |
| SEC-003 | CWE-256 | **High** | `app/cpy/CSUSR01Y.cpy:21` | `SEC-USR-PWD PIC X(08)` stored plaintext in USRSEC KSDS. COSGN00C compares directly: `IF SEC-USR-PWD = WS-USER-PWD` (`cbl/COSGN00C.cbl:223`). | Replace with salted hash (IBM ICSF CSNBOWH on z/OS; BCrypt in Java). Change login to hash-compare. |
| SEC-004 | CWE-312 | **High** | `app/cbl/COUSR02C.cbl:169` | Stored plaintext password sent back to the 3270 terminal screen on user-update. Visible in session logs and screen captures. | Never populate password field on update screen. Require explicit new-password entry. Apply same rule in Java. |
| SEC-006 | CWE-312 | **High** | `app/cpy/CUSTREC.cpy:17-20` | `CUST-SSN PIC 9(09)`, `CUST-GOVT-ISSUED-ID PIC X(20)`, `CUST-EFT-ACCOUNT-ID PIC X(10)` stored as plaintext in CUSTDATA KSDS. | Encrypt at rest (IBM ICSF on z/OS; AES-256 / AWS RDS column encryption in Java). |
| SEC-008 | CWE-307 | **High** | `app/cbl/COSGN00C.cbl` (full) | No failed-login counter, no account lockout, no delay. Unlimited password guesses with no consequence on CICS terminals. | Implement lockout after 5 failures. In Java: Spring Security `UserDetails.isAccountNonLocked()` + Bucket4j rate limiter. |
| SEC-009 | CWE-862 | **High** | `app/cbl/COUSR01C.cbl`, `COUSR02C.cbl`, `COUSR03C.cbl` | User-add/update/delete programs never verify the invoking user's type is admin. Menu-only guard. A regular user who knows the CICS transaction ID (`CU01`/`CU02`/`CU03`) can invoke them directly. | Add `IF NOT CDEMO-USRTYP-ADMIN PERFORM RETURN-TO-PREV-SCREEN` at top of each. In Java: `@PreAuthorize("hasRole('ADMIN')")` on all user-management service methods. |
| SEC-012 | CWE-319 | **High** | `app/jcl/FTPJCL.JCL:30-40`, `scripts/remote_compile.sh:35` | All data transfers — including cardholder datasets and source files — over plain FTP (port 2121 tunnel). PII and credentials transmitted in cleartext. | Replace with SFTP/FTPS. In Java migration, use HTTPS + TLS 1.3 for all data movement. |
| SEC-007 | CWE-261 | **Medium** | `app/cbl/COSGN00C.cbl:135` | Password uppercased before comparison: `MOVE FUNCTION UPPER-CASE(PASSWDI) TO WS-USER-PWD`. Case-insensitive auth reduces effective entropy. `Password1` = `PASSWORD1`. | Remove UPPER-CASE transform on password. In Java: BCrypt/Argon2 handles encoding correctly; never uppercase. |
| SEC-010 | CWE-20 | **Medium** | `app/cbl/COUSR01C.cbl:142-158` | `USRTYPEI` validated only for non-empty. No check that value is `'A'` or `'U'`. Arbitrary character can be stored as user type. | Validate against explicit allowlist. In Java: enum with fixed roles; reject anything not in the set. |
| SEC-011 | CWE-78 | **Medium** | `scripts/remote_compile.sh:27,35-39` | `$file_basename_no_extension` used unquoted in `sed` substitution and FTP heredoc. Shell metacharacters in filename → code injection. | Quote all expansions. Validate against `^[A-Z0-9]{1,8}$` before use. |
| SEC-013 | CWE-732 | **Medium** | `app/jcl/DUSRSECJ.jcl:82-83`, `CBEXPORT.jcl:49-63` | No RACF dataset profiles documented. Sensitive datasets (`USRSEC.*`, `CUSTDATA.*`, `CARDDATA.*`) referenced with `DISP=SHR` with no `UACC(NONE)` or `PERMIT` evidence. | Define RACF profiles with `UACC(NONE)`. In Java: IAM roles with least-privilege on RDS/S3/DynamoDB. |
| SEC-014 | CWE-20 | **Medium** | `app/cbl/CBIMPORT.cbl:449-453` | `3000-VALIDATE-IMPORT` contains only display statements — no actual validation. Customer records written to production VSAM without SSN format check, date validation, or credit-limit range check. | Implement field-level validation. In Java: Bean Validation (`@NotNull`, `@Pattern`, `@Range`) + custom `ImportValidator` before any JPA `.save()`. |

---

## Documentation Gaps

Top 5 undocumented behaviors a new engineer would need explained:

1. **Incomplete functionality in 4 core programs** — `CODING-TO-BE-DONE VALUE 'Looks Good.... so far'` in COACTUPC, COCRDUPC, COACTVWC, COCRDSLC signals unfinished work. No source comment, change log, or README explains what was planned. A Java migration will reproduce the same gap unless an SME identifies the missing requirements.

2. **CORPT00C TDQ write content** — CORPT00C submits batch JCL via the CICS internal reader TDQ (`cbl/CORPT00C.cbl:517`). The JCL content assembled at runtime is not documented anywhere in the source. Without knowing which JCL skeleton it constructs, a Java equivalent cannot be written correctly.

3. **CICS option 11 PGMIDERR hazard** — `COMEN01C` dispatches to `COPAUS0C` (auth extension) on menu option 11 with no `HANDLE CONDITION PGMIDERR` guard (unlike `COADM01C` which has one). Every base installation without the auth extension will abend on option 11 selection. No README or inline comment documents this dependency.

4. **EOD batch pipeline trigger chain** — The CA7 file shows three jobs in sequence (CLOSEFIL → CBPAUP0J → POSTTRAN), but the full production EOD pipeline — including INTCALC, CREASTMT, and TRANREPT sequencing — is not documented. The `CardDemo.controlm` and `CardDemo.ca7` files together contain the truth, but they have not been synthesized into a readable pipeline diagram anywhere.

5. **DALYREJS reject handling** — `CBTRN01C` writes validation rejects to a DALYREJS GDG (`jcl/CBTRN01C` output), and `CBTRN03C` produces a reject report. No documentation explains whether rejects are: (a) manually reviewed and resubmitted, (b) automatically reprocessed, or (c) permanently discarded. This is a business-rule gap, not a code gap — the Java migration must implement whatever the right answer is.

---

## Relative Scale

**COCOMO-II basic index (scale signal only — not a timeline or cost):**

| Scope | KSLOC | Index = 2.94 × KSLOC^1.10 |
|---|---|---|
| COBOL programs only (migration core) | 33.9 | **~148** |
| Full system (all languages) | 41.7 | **~178** |

**These numbers are a relative complexity/scale indicator** for ranking CardDemo against other systems in a portfolio. They assume traditional human-team productivity. Agentic transformation does not follow those productivity curves — do not treat these as person-months, a schedule, or a cost estimate.

For context: the COBOL core is mid-size for a mainframe application. The dominant complexity driver is not LOC but the god-program (COACTUPC, CCN 122) and the absence of any test suite, both of which increase characterization and validation effort.

---

## Recommended Modernization Pattern

**Pattern: Rearchitect → `/modernize-transform`**

CardDemo's COBOL→Java migration is a full cross-stack language rewrite, not a same-stack version bump, making `/modernize-uplift` inapplicable. The system's 10 functional domains map cleanly to a layered Spring Boot application: Spring Batch for EOD pipeline (D6–D7), Spring MVC REST controllers for online transactions (D3–D5), Spring Security for authentication (D1), and Spring Data JPA (with H2 for PoC, Aurora/RDS for production) replacing VSAM. The three critical security findings (CVV storage, plaintext credentials, missing authorization checks) are not bugs to fix in the COBOL — they are intentional architectural decisions that must be **replaced**, not **translated**, in Java, making a clean rearchitect preferable to a line-by-line lift.

**Execution order (per `PREFLIGHT.md` Premise P4 — batch first):**
1. Start with D6 batch programs (CBTRN01C, CBTRN02C, CBACT04C) — no CICS dependency, clear input→output contracts from JCL, immediately testable with Spring Batch + JUnit 5.
2. Then D7 reporting (CBSTM03A, CBTRN03C) and D8 migration (CBEXPORT, CBIMPORT).
3. Then D2 navigation shell and D1 auth with Spring Security (replaces USRSEC VSAM + plaintext password).
4. Then D3–D5 online screens as Spring MVC REST + Thymeleaf (replaces BMS maps).
5. D9 optional extensions are out of scope for the PoC.

**Next command:** `/modernize-map CardDemo` — builds the program-level dependency topology and data-flow map needed to sequence the individual transformation units.

---

*Credential inventory: `analysis/CardDemo/SECRETS.local.md` (gitignored — not for sharing)*  
*Architecture diagram: `analysis/CardDemo/ARCHITECTURE.mmd`*  
*Preflight + migration premises: `analysis/CardDemo/PREFLIGHT.md`*
