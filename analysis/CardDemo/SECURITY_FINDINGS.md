# CardDemo — Security Findings

**Generated:** 2026-09-14  
**Scope:** `legacy/CardDemo/` (COBOL/JCL) + `modernized/CardDemo/` (Java 21 / Spring Boot 3.3.5)  
**Method:** Adversarial audit — read-only; all Critical/High confirmed by code before inclusion.  
**Prompt-injection scan:** 0 matches across all source files.  
**Findings refuted and dropped:** 0

---

## Summary Scorecard

| Severity | New (this audit) | Prior (confirmed) | Total |
|---|---|---|---|
| Critical | 2 (SEC-001, SEC-017) | 0 | 2 |
| High | 5 (SEC-004, SEC-015, SEC-016, SEC-018, SEC-009) | 0 | 5 |
| Medium | 2 (SEC-019, SEC-020) | 0 | 2 |
| Low | 1 (SEC-021) | 0 | 1 |
| **Total** | **10** | | **10** |

Previously catalogued and already applied in Java (not re-patched):  
SEC-003 (BCrypt), SEC-005 (CVV absent), SEC-009 (ADMIN @PreAuthorize on user management), SEC-014 (Bean Validation on import)

---

## Findings Table

| ID | CWE | Severity | Layer | File:Line | Summary |
|---|---|---|---|---|---|
| SEC-001 | CWE-259 | **Critical** | Legacy-COBOL | `legacy/CardDemo/app/jcl/FTPJCL.JCL:33-35` | FTP credentials in plaintext in-stream JCL — see SECRETS.local.md (gitignored) |
| SEC-004 | CWE-312 | **High** | Legacy-COBOL | `legacy/CardDemo/app/cbl/COUSR02C.cbl:169` | Plaintext stored password MOVE'd to 3270 screen field on user-update screen |
| SEC-015 | CWE-639 | **High** | Modernized-Java | `carddemo-web/.../account/AccountViewController.java:30`, `AccountUpdateController.java:59,77` | Account IDOR — any authenticated user can view/edit any account by ID |
| SEC-016 | CWE-639 | **High** | Modernized-Java | `carddemo-web/.../card/CardListController.java:34`, `CardDetailController.java:30`, `CardUpdateController.java:37,51` | Card IDOR — any user can list/view/edit any card number; JavaDoc claim of scope enforcement has zero enforcement code |
| SEC-017 | CWE-639 | **Critical** | Modernized-Java | `carddemo-web/.../billing/BillPaymentController.java:52,67`, `BillPaymentService.java:55-79` | Bill-payment IDOR — single HTTP request pair reads and zeroes any account balance |
| SEC-018 | CWE-284 | **High** | Modernized-Java | `carddemo-web/.../transaction/TransactionAddController.java:57`, `TransactionListController.java:28` | (a) RULE-051 bypass: POST /transactions/add commits to DB before user confirms; (b) transaction history visible for any card number |
| SEC-019 | CWE-916 | **Medium** | Modernized-Java | `carddemo-web/.../user/UserAddController.java:49`, `UserUpdateController.java:49` | CVE-2025-22228: BCrypt silently truncates passwords >72 chars; no guard in encode() callers |
| SEC-020 | CWE-307 | **Medium** | Modernized-Java | `carddemo-web/.../auth/SecurityConfig.java:58-81` | No brute-force protection on POST /login — unlimited password-guess rate |
| SEC-021 | CWE-20 | **Low** | Modernized-Java | `carddemo-web/.../account/AccountUpdateValidator.java:38-42` | Negative credit limit accepted — null check only, no non-negative guard |
| SEC-009 | CWE-862 | **High** | Legacy-COBOL | `legacy/CardDemo/app/cbl/COUSR01C.cbl:78`, `COUSR02C.cbl:84`, `COADM01C.cbl:86` | CICS admin programs check only EIBCALEN; crafted COMMAREA bypasses all admin auth (Java layer already fixed with @PreAuthorize) |

> **3 hardcoded credentials found** — inventory in `analysis/CardDemo/SECRETS.local.md` (gitignored; not for sharing).

---

## Finding Details

### SEC-001 — FTP Credentials in JCL (Critical)

**CWE-259 — Use of Hard-coded Password**  
`legacy/CardDemo/app/jcl/FTPJCL.JCL:33-35`

The in-stream SYSIN for PGM=FTP contains a cleartext username and password (see SECRETS.local.md, row S-01). These credentials target an RFC-1918 FTP server and are present in public git history — treat as compromised regardless of whether the server is currently reachable.

**Exploit scenario:** Anyone with repository read access extracts the credentials and authenticates to the FTP server, gaining access to cardholder-scope VSAM dataset transfers.

**Fix:** Rotate the FTP account immediately. Replace the in-stream credentials with RACF PassTickets for the job's submitter ID, or migrate to SFTP with SSH key authentication. Scrub git history using `git filter-repo` or BFG Repo-Cleaner.

---

### SEC-004 — Plaintext Password Exposed on 3270 Screen (High)

**CWE-312 — Cleartext Storage/Transmission of Sensitive Information**  
`legacy/CardDemo/app/cbl/COUSR02C.cbl:169`

```cobol
MOVE SEC-USR-PWD        TO PASSWDI   OF COUSR2AI
```

The stored plaintext password is sent to the 3270 terminal password field when an admin opens the user-update screen. This exposes the credential in terminal session recordings, CICS trace logs, and 3270 screen captures.

**Fix:** Comment out this MOVE; the update flow already handles blank PASSWDI by preserving the existing password (line 198: `WHEN PASSWDI OF COUSR2AI = SPACES OR LOW-VALUES`). The Java `UserUpdateController.java:47` already handles this correctly.

---

### SEC-015 — Account IDOR (High)

**CWE-639 — Authorization Bypass Through User-Controlled Key**  
`AccountViewController.java:30`, `AccountUpdateController.java:59,77`

`GET /accounts/{id}` and `GET/POST /accounts/{id}/edit` accept any numeric account ID with no check that the authenticated principal owns that account. The `accountQueryService.findAccount(id)` call at line 30 uses the user-supplied path variable directly.

**Root cause:** `UserEntity` has no foreign-key relationship to `AccountEntity` or `CustomerEntity`. The COBOL system carried the current account context in COMMAREA; the Java layer has no equivalent binding.

**Fix (patch):** Add `@PreAuthorize("hasRole('ADMIN')")` as a temporary protective restriction on all account-management endpoints until a `customer_id` column is added to `app_user` and an ownership check is wired in (see patch comment for production path).

---

### SEC-016 — Card IDOR (High)

**CWE-639 — Authorization Bypass Through User-Controlled Key**  
`CardListController.java:34`, `CardDetailController.java:30`, `CardUpdateController.java:37,51`

`GET /accounts/{accountId}/cards`, `GET /cards/{cardNumber}`, and `GET/POST /cards/{cardNumber}/edit` perform repository lookups with no principal ownership check. `CardListController.java` carries a JavaDoc comment stating "non-admin sees only cards for their account" but contains zero enforcement code.

**Fix (patch):** Remove the misleading JavaDoc, add explicit access guards, and document the missing ownership model.

---

### SEC-017 — Bill-Payment IDOR — Zero Any Account Balance (Critical)

**CWE-639 — Authorization Bypass Through User-Controlled Key**  
`BillPaymentController.java:52,67`, `BillPaymentService.java:55-79`

1. `POST /billing/confirm?accountId=<victim>` reveals the victim account's full current balance (no ownership check at line 52).
2. `POST /billing/pay?accountId=<victim>&confirmed=Y` triggers `BillPaymentService.pay()`, which reads the balance (line 60), creates a `BILL PAYMENT` transaction record, and zeroes `ACCT-CURR-BAL` (line 79) — all without verifying the caller owns account `<victim>`.

This is a single-request financial attack achievable by any authenticated user.

**Fix (patch):** Restrict `confirm` and `pay` to `ROLE_ADMIN` until the user→account ownership link exists. Document production path.

---

### SEC-018 — Transaction RULE-051 Bypass + List IDOR (High)

**CWE-284 — Improper Access Control**  
`TransactionAddController.java:44-66`, `TransactionListController.java:28`

**(a) RULE-051 bypass:** `POST /transactions/add` calls `transactionService.add(req)` at line 57. `TransactionService.add()` is `@Transactional` and executes `transactionRepository.save(tran)` at line 104 — committing a transaction record to the database before the user has confirmed anything. A comment in the code at line 58-59 acknowledges this as a known simplification: `"If add succeeded, we went past validation — this is fine for step1 in tests."`. As written, every form submission with valid input creates a committed financial transaction.

**(b) List IDOR:** `GET /transactions?cardNumber=<any>` returns the full transaction history for any card number with no ownership check.

**Fix (patch):** (a) Split `TransactionService.add()` into `validate(req)` (checks only, no DB write) and `save(req)` (persists). The step-1 validate handler calls `validate()`; the step-2 confirm handler calls `save()`. (b) Restrict transaction list to ADMIN until ownership model exists.

---

### SEC-019 — BCrypt Silent Truncation / CVE-2025-22228 (Medium)

**CWE-916 — Use of Password Hash With Insufficient Computational Effort (variant)**  
`UserAddController.java:49`, `UserUpdateController.java:49`

Spring Security 6.3.4 (resolved from Spring Boot 3.3.5 BOM) is vulnerable to CVE-2025-22228: `BCryptPasswordEncoder.encode()` silently truncates input longer than 72 bytes. A user with password `A*73` (72 matching chars + extras) authenticates as if the extras never existed — anyone who knows the first 72 characters can log in.

**Fix (patch):** Add an explicit length guard before encoding:
```java
if (password.length() > 72) throw validation error / model error
```
Long-term: upgrade to Spring Boot 3.3.7+ (Spring Security 6.3.5) which adds this check inside the encoder.

---

### SEC-020 — No Brute-Force Protection on /login (Medium)

**CWE-307 — Improper Restriction of Excessive Authentication Attempts**  
`SecurityConfig.java:58-81`

`SecurityConfig.filterChain()` configures form login with no `AuthenticationFailureHandler` that counts failures and no account-lockout mechanism. An attacker can submit unlimited password-guessing requests to `POST /login`.

**Fix:** Add failure counting in `UserDetailsServiceImpl` (persist failure counter in `app_user` table) and a `UserDetails.isAccountNonLocked()` check, OR add Bucket4j with a 5-attempts-per-15-minute-window filter. This finding requires manual remediation — no patch hunk included (touches DB schema and Spring config).

---

### SEC-021 — Negative Credit Limit Accepted (Low)

**CWE-20 — Improper Input Validation**  
`AccountUpdateValidator.java:38-42`

The `creditLimit` and `cashCreditLimit` fields are validated only for null at lines 38-42. A submission of `creditLimit=-9999999.00` passes validation and corrupts the account record.

**Fix (patch):** Add non-negative (and maximum ceiling) guards to both limit fields.

---

### SEC-009 — CICS Admin Programs Auth Bypass (High — Legacy COBOL)

**CWE-862 — Missing Authorization**  
`COUSR01C.cbl:78`, `COUSR02C.cbl:84`, `COADM01C.cbl:86`

Each admin program's `MAIN-PARA` checks only `IF EIBCALEN = 0` (no COMMAREA → redirect to signon). A CICS user who knows the transaction ID (`CU01`, `CU02`, `CU03`, `CA00`) can invoke any admin program directly with a crafted COMMAREA that contains a valid user record with `CDEMO-USRTYP-USER`. The user-type field in the COMMAREA is never verified within the admin programs.

**Java layer:** already fixed — `SecurityConfig` restricts `/admin/**` to `ROLE_ADMIN`; controllers carry `@PreAuthorize("hasRole('ADMIN')")`.  
**COBOL fix (patch):** Add `IF NOT CDEMO-USRTYP-ADMIN PERFORM RETURN-TO-SIGNON-SCREEN` at the top of each admin program's `MAIN-PARA`.

---

## Dependency CVE Table

| Package | Version | CVE | Fixed In | Severity |
|---|---|---|---|---|
| `spring-security-crypto` | 6.3.4 | CVE-2025-22228 (BCrypt truncation) | 6.3.5 (Spring Boot 3.3.7+) | Medium |
| All others (H2 2.2.224, Jackson 2.17.2, Hibernate 6.5.3, Thymeleaf 3.1.2) | — | No active CVEs found | — | — |

---

## Remediation Log

| ID | Severity | Patch File | Summary |
|---|---|---|---|
| SEC-001 | Critical | `security_remediation.local.patch` | Replace plaintext FTP credentials with RACF/SFTP placeholder; see hunk for commented-out credential block |
| SEC-004 | High | `security_remediation.patch` | Comment out `MOVE SEC-USR-PWD TO PASSWDI` in COUSR02C.cbl:169 |
| SEC-015 | High | `security_remediation.patch` | Add `@PreAuthorize("hasRole('ADMIN')")` to `AccountViewController` and `AccountUpdateController` (temporary — pending user→account ownership link) |
| SEC-016 | High | `security_remediation.patch` | Remove misleading JavaDoc; add access guard to `CardListController`, `CardDetailController`, `CardUpdateController` |
| SEC-017 | Critical | `security_remediation.patch` | Add principal ownership guard in `BillPaymentController.confirm()` and `pay()` |
| SEC-018(a) | High | `security_remediation.patch` | Split `TransactionService.add()` into `validate()` + `save()`; wire correctly in `TransactionAddController` |
| SEC-018(b) | High | `security_remediation.patch` | Add access guard to `TransactionListController` |
| SEC-019 | Medium | `security_remediation.patch` | Add password-length guard in `UserAddController` and `UserUpdateController` |
| SEC-020 | Medium | — | **Needs manual remediation** — requires DB schema change (failure counter) and new Spring Security config |
| SEC-021 | Low | `security_remediation.patch` | Add non-negative + ceiling validation for credit limits in `AccountUpdateValidator` |
| SEC-009 | High | `security_remediation.patch` | Add admin-type assertion in COBOL `MAIN-PARA` for COUSR01C, COUSR02C, COADM01C |

**Structural gap — SEC-015/016/017/018 IDOR (user→account ownership model):**  
`UserEntity` has no FK to `CustomerEntity`, `CardXRefEntity`, or `AccountEntity`. The COBOL system carried current account context in COMMAREA (CDEMO-ACCT-ID); the Java layer has no equivalent binding. The patches apply `@PreAuthorize("hasRole('ADMIN')")` as a temporary protective measure. Production remediation requires adding a `customer_id` column to `app_user` and an `OwnershipService` that resolves `user.customerId → CardXRef.customerId → CardXRef.accountId`, then replacing the ADMIN-only guard with `ownershipService.ownsAccount(principal, accountId)`.

---

## Patch Review

Review round: **2 of 3** (round 1 produced 1 PARTIAL + 2 INTRODUCES-RISK; all corrected in round 2).

| Finding | Hunk scope | R1 verdict | R2 correction | R2 verdict |
|---|---|---|---|---|
| SEC-001 | FTPJCL.JCL credential removal | RESOLVES | — | RESOLVES |
| SEC-004 (hunk 1) | COUSR02C.cbl — remove MOVE SEC-USR-PWD | RESOLVES | — | RESOLVES |
| SEC-004 (hunk 2) | COUSR02C.cbl — UPDATE-USER-INFO PASSWDI=SPACES branch | PARTIAL (error not preserved; field-only updates broken) | Changed branch from error to `CONTINUE` so blank = keep existing | RESOLVES |
| SEC-009 COUSR01C | Admin check after COMMAREA copy | INTRODUCES-RISK (`RETURN-TO-SIGNON-SCREEN` does not exist) | Corrected to `RETURN-TO-PREV-SCREEN` + added `MOVE 'COSGN00C'`; placed inside ELSE after COMMAREA copy | RESOLVES |
| SEC-009 COUSR02C | Admin check after COMMAREA copy | INTRODUCES-RISK (same paragraph name defect) | Same correction as COUSR01C | RESOLVES |
| SEC-009 COADM01C | Admin check at MAIN-PARA entry | RESOLVES | — | RESOLVES |
| SEC-015 AccountViewController | `@PreAuthorize` + import | INTRODUCES-RISK (import treated as pre-existing context; hunk mismatch aborts patch) | Import added as new `+` line; separate hunk from class annotation | RESOLVES |
| SEC-015 AccountUpdateController | `@PreAuthorize` + import | RESOLVES | — | RESOLVES |
| SEC-016 CardListController | Remove false JavaDoc + `@PreAuthorize` | RESOLVES | — | RESOLVES |
| SEC-016 CardDetailController | `@PreAuthorize` + import | RESOLVES | — | RESOLVES |
| SEC-016 CardUpdateController | `@PreAuthorize` + import | RESOLVES | — | RESOLVES |
| SEC-017 BillPaymentController | `@PreAuthorize` on confirm + pay | RESOLVES | — | RESOLVES |
| SEC-018(a) TransactionService | Split `add()` → `validate()` + `save()` | RESOLVES | — | RESOLVES |
| SEC-018(a) TransactionAddController | Step 1 → `validate()`; step 2 → `save()` | RESOLVES | — | RESOLVES |
| SEC-018(b) TransactionListController | `@PreAuthorize` + import | RESOLVES | — | RESOLVES |
| SEC-019 UserAddController | Password length guard | RESOLVES | — | RESOLVES |
| SEC-019 UserUpdateController | Password length guard on change path | RESOLVES | — | RESOLVES |
| SEC-021 AccountUpdateValidator | Non-negative + ceiling guards | RESOLVES | — | RESOLVES |

**Credential hygiene confirmed:** No raw credential values appear in `security_remediation.patch`. Raw removal lines appear only in `security_remediation.local.patch` (gitignored), as required.  
**`@EnableMethodSecurity` confirmed present** in `SecurityConfig.java:35` — all `@PreAuthorize` annotations will be enforced.  
**`TransactionService.save()` guard confirmed:** `save()` calls `validate()` internally — no bypass path exists.

---

## How to Apply

```bash
# Shareable patch — apply from project root:
git apply analysis/CardDemo/security_remediation.patch

# Credential patch — apply from project root (rotate credentials first):
git apply analysis/CardDemo/security_remediation.local.patch

# Re-run verification after applying:
cd modernized/CardDemo && mvn test
```
