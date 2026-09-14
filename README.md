# CardDemo — Mainframe Modernization PoC

**Target:** Java 21 + Spring Boot 3.3.5 (Spring Batch, Spring MVC, Spring Security, Spring Data JPA)  
**Source:** IBM Enterprise COBOL v6.3 / CICS / VSAM / z/OS JES2 batch  
**Scope:** Exploratory proof-of-concept — demonstrates that COBOL business logic can be correctly expressed in Java before any production investment is committed.

---

## What this repository contains

```
legacy/CardDemo/          ← original COBOL source (106 programs + copybooks, 33,876 LOC)
analysis/CardDemo/        ← modernization assessment artifacts
  ASSESSMENT.md           ← risk, complexity, security findings
  BUSINESS_RULES.md       ← 70+ business rules in Given/When/Then form
  DATA_OBJECTS.md         ← VSAM data structures inventory
  MODERNIZATION_BRIEF.md  ← 5-phase execution plan (binding document)
  PREFLIGHT.md            ← environment readiness report
  BASELINE.md             ← golden-master expected outputs per batch program
modernized/CardDemo/      ← Java target (Maven multi-module, Spring Boot 3.3.5)
```

---

## Modernized project structure

```
modernized/CardDemo/
├── pom.xml                          Parent POM (Java 21, Spring Boot 3.3.5)
├── carddemo-common/                 COBOL display-format parsers, utilities
├── carddemo-domain/                 JPA entities + repositories (H2 in-memory for PoC)
├── carddemo-batch-eod/              Phase 1 — EOD batch pipeline
└── carddemo-batch-reporting/        Phase 2 — Reporting, statements, data migration
```

### Domain model (`carddemo-domain`)

| Entity | Maps from | Key fields |
|---|---|---|
| `AccountEntity` | ACCTDATA VSAM KSDS | id, activeStatus, currentBalance, creditLimit, cycleCredit/Debit, expirationDate |
| `CardXRefEntity` | CARDXREF VSAM KSDS | cardNumber (PK), customerId, accountId |
| `CardEntity` | CARDDATA VSAM KSDS | cardNumber (PK) — CVV field absent (SEC-005 / PCI DSS fix) |
| `CustomerEntity` | CUSTFILE VSAM KSDS | id, firstName/middleName/lastName, address fields, ficoCreditScore |
| `TransactionEntity` | TRANSACT VSAM KSDS | id (String PK), typeCode, categoryCode, amount, cardNumber, timestamps |
| `TranCatBalanceEntity` | TCATBAL VSAM KSDS | composite key (accountId, typeCode, categoryCode), balance |
| `DiscountGroupEntity` | DISCGRP VSAM KSDS | composite key (groupId, typeCode, categoryCode), interestRate |

---

## Phase status

### Phase 1 — Batch Foundation + EOD Pipeline ✅ COMPLETE

**43/43 tests GREEN** | `mvn test` → `BUILD SUCCESS`

| COBOL | LOC | Java target | Rules proven |
|---|---|---|---|
| CBTRN01C | 494 | `TransactionValidationJob` | RULE-063, RULE-064 (card/account validation) |
| CBTRN02C | 731 | `TransactionPostingJob` | RULE-063–066 (reject codes 100–103), RULE-059, RULE-061 (atomic rollback), RULE-065 (overlimit), RULE-066 (expiry) |
| CBACT04C | 652 | `InterestCalculationJob` | RULE-007 (RoundingMode.DOWN), RULE-008/009 (cycle reset) |

Key fixes applied vs. COBOL:
- **RULE-061 (TD-07):** `@Transactional(SERIALIZABLE, REQUIRES_NEW)` — COBOL left partial state on write failure
- **SEC-003:** BCrypt replaces plaintext password comparison (Phase 3)
- **SEC-005 / PCI DSS:** CVV field removed from `CardEntity` and all API surfaces

### Phase 2 — Reporting, Statements & Data Migration ✅ COMPLETE

| COBOL | LOC | Java target | Notes |
|---|---|---|---|
| CBSTM03A + CBSTM03B | 1,154 | `StatementGenerationJob` + `StatementFormatter` | Dropped: PSA/TCB/TIOT z/OS block addressing; ALTER/GO TO; 51×10 in-memory table |
| CBACT01C | ~200 | `AccountListReportJob` | Sequential account scan |
| CBACT02C | ~200 | `CardReportJob` | No CVV field (SEC-005) |
| CBACT03C | ~200 | `CrossRefReportJob` | XREF sequential scan |
| CBCUS01C | ~200 | `CustomerReportJob` | Customer list |
| CBTRN03C | ~649 | `TransactionReportJob` | RULE-021 (three-level totaling), RULE-022 (page size), RULE-023 (date-range filter) |
| CBEXPORT | ~582 | `DataExportJob` | Multi-record CSV export |
| CBIMPORT | ~487 | `DataImportJob` | SEC-014 fix: Bean Validation (SSN format, credit-limit range, date format) |

### Phase 3 — Authentication & Navigation (first online controller) ⏳ PENDING

Pilot: `COSGN00C` → `AuthController` + Spring Security  
Scope: COSGN00C, COUSR00-03C, COMEN01C, COADM01C + CICS adaptation layer (`CicsAid`, `BmsAttr`, `CicsContext`)

### Phase 4 — Account & Card Online (god-program decomposition) ⏳ PENDING

Requires Phases 2 + 3. Pilot: `COACTVWC` → `AccountViewController`  
Scope: COACTUPC (4,236 LOC, CCN 122), COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC

### Phase 5 — Transactions & Billing Online ⏳ PENDING

Requires Phase 4. Pilot: `COBIL00C` → `BillPaymentController`  
Scope: COTRN00-02C, COBIL00C, CORPT00C

---

## Quick start

**Prerequisites:** Java 21+, Maven 3.9+

```bash
# Build and run all tests
cd modernized/CardDemo
mvn test

# Start the EOD batch application (no jobs run on startup)
mvn -pl carddemo-batch-eod spring-boot:run \
    -Dspring-boot.run.arguments="--spring.batch.job.enabled=false"

# Start the reporting application
mvn -pl carddemo-batch-reporting spring-boot:run \
    -Dspring-boot.run.arguments="--spring.batch.job.enabled=false"
```

Expected output:
```
Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Test coverage

| Module | Tests | Coverage |
|---|---|---|
| `carddemo-batch-eod` | 25 | CBTRN01C (7), CBTRN02C (10), CBACT04C (8) |
| `carddemo-batch-reporting` | 18 | StatementGeneration (5), Phase2Jobs (13) |
| **Total** | **43** | **43/43 GREEN** |

**Equivalence strategy:** Golden-master characterization tests (trace-based — no z/OS runtime available). GnuCOBOL 3.2.0 syntax check passes for all COBOL sources with `-I cpy/` flag.

---

## Key design decisions (from `MODERNIZATION_BRIEF.md`)

| Decision | COBOL behavior | Java behavior | Rule |
|---|---|---|---|
| Interest rounding | `COMPUTE` without `ROUNDED` → truncate | `RoundingMode.DOWN` | RULE-007 |
| Inactive accounts in batch | No check on `ACCT-ACTIVE-STATUS` | Replicated as-is + TODO comment | RULE-059 |
| Overlimit formula | Cycle-to-date only (no carry-forward) | `credit - debit + amount` | RULE-065 |
| Expiry check | String compare YYYY-MM-DD | Strict `LocalDate.parse()` | RULE-066 |
| Transaction ID (batch) | Sequential counter | Same (no RULE-012 fix needed here) | — |
| Atomic rollback | No rollback on partial write failure | `@Transactional(SERIALIZABLE)` | RULE-061 |
| CVV storage | Stored in CARDDATA | Field removed (PCI DSS / SEC-005) | — |
| Password storage | Plaintext comparison | BCrypt (Phase 3) | SEC-003 |
| Missing authorization | No admin check in COUSR01-03C | `@PreAuthorize("hasRole('ADMIN')")` (Phase 3) | SEC-009 |
| DALYREJS reject disposition | No auto-resubmit in COBOL | Structured CSV reject file | Q6/DG-5 |

---

## Artifacts

| Artifact | Path | Purpose |
|---|---|---|
| Modernization brief | `analysis/CardDemo/MODERNIZATION_BRIEF.md` | Binding execution plan; edit here to steer phases |
| Business rules catalog | `analysis/CardDemo/BUSINESS_RULES.md` | 70+ rules in Given/When/Then form |
| Data objects inventory | `analysis/CardDemo/DATA_OBJECTS.md` | All VSAM structures with field layouts |
| Dependency topology | `analysis/CardDemo/TOPOLOGY.html` | Interactive call-graph viewer |
| Baseline | `modernized/CardDemo/BASELINE.md` | Golden-master expected outputs |
| Phase 1 playbook | `modernized/CardDemo/PHASE1_PLAYBOOK.md` | Surprises found, patterns for remaining programs |
| Transformation notes | `modernized/CardDemo/carddemo-batch-eod/TRANSFORMATION_NOTES.md` | COBOL→Java line-level mapping |

---

## Security findings addressed

| Finding | Severity | Status |
|---|---|---|
| SEC-003: Plaintext password storage | HIGH | ✅ Phase 3: BCrypt replaces plaintext comparison |
| SEC-005: CVV stored in CARDDATA | CRITICAL (PCI DSS) | ✅ CVV field absent from `CardEntity` and all APIs |
| SEC-009: Missing admin authorization on user management | HIGH | ✅ Phase 3: `@PreAuthorize("hasRole('ADMIN')")` |
| SEC-014: No input validation on import | HIGH | ✅ Phase 2: Bean Validation on `DataImportJob` |

---

## Branch

All modernization work is on branch `modernize`. The main branch contains the original legacy source.

```bash
git checkout modernize    # modernization artifacts + Java target
git checkout main         # original COBOL source only
```
