# TRANSFORMATION NOTES — Phase 3: Authentication & Navigation

**Legacy:** `legacy/CardDemo/app/cbl/COSGN00C.cbl` (261 LOC) · `COUSR00C.cbl` (696 LOC) · `COUSR01C.cbl` (306 LOC) · `COUSR02C.cbl` (420 LOC) · `COUSR03C.cbl` (360 LOC) · `COMEN01C.cbl` (309 LOC) · `COADM01C.cbl` (295 LOC)  
**Target:** `carddemo-web` module — `AuthController`, `UserDetailsServiceImpl`, `SecurityConfig`, `UserListController`, `UserAddController`, `UserUpdateController`, `UserDeleteController`, `MenuController`, `AdminMenuController`, `CicsContext`, `CicsAid`, `BmsAttr`  
**Date:** 2026-09-13  
**Status:** 20/20 characterization tests GREEN

---

## Behavior Mapping

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| COSGN00C.cbl:80–83 | `MAIN-PARA` — `IF EIBCALEN = 0` → `SEND-SIGNON-SCREEN` (blank screen on first entry) | `AuthController.java:21–32` | `AuthController.loginPage()` — `GET /login` renders login Thymeleaf view |
| COSGN00C.cbl:117–122 | `PROCESS-ENTER-KEY` — blank UserID check (RULE-001): `USERIDI = SPACES OR LOW-VALUES` → `'Please enter User ID ...'` | `SecurityConfig.java` | Spring Security `DaoAuthenticationProvider` → `UsernameNotFoundException` → redirect `/login?error=true` |
| COSGN00C.cbl:123–127 | Blank password check (RULE-002): `PASSWDI = SPACES OR LOW-VALUES` → `'Please enter Password ...'` | `SecurityConfig.java` | Spring Security `BadCredentialsException` → redirect `/login?error=true` |
| COSGN00C.cbl:132–136 | `FUNCTION UPPER-CASE(USERIDI)` → `WS-USER-ID` before file lookup | `UserDetailsServiceImpl.java:35` | `userRepository.findById(userId.toUpperCase())` |
| COSGN00C.cbl:209–219 | `READ-USER-SEC-FILE` — `EXEC CICS READ DATASET('USRSEC')` keyed by userId (RULE-003) | `UserDetailsServiceImpl.java:34–37` | `userRepository.findById()` → `UsernameNotFoundException` on NOTFND (RESP=13) |
| COSGN00C.cbl:223–246 | Password comparison: `IF SEC-USR-PWD = WS-USER-PWD` (RULE-004); plain-text compare, input uppercased | `SecurityConfig.java:50–54` | `BCryptPasswordEncoder.matches()` in `DaoAuthenticationProvider` (SEC-003 fix — see Deliberate Deviations) |
| COSGN00C.cbl:230–239 | Role dispatch after auth: `IF CDEMO-USRTYP-ADMIN → XCTL COADM01C ELSE XCTL COMEN01C` (RULE-005) | `SecurityConfig.java:88–94` | `AuthenticationSuccessHandler.successHandler()` — `ROLE_ADMIN` → `redirect:/admin/menu`; `ROLE_USER` → `redirect:/menu` |
| COSGN00C.cbl:89 | `WHEN DFHPF3` → `SEND-PLAIN-TEXT` (thank-you message, session ends) (RULE-055) | `SecurityConfig.java:73–79` | `logout()` — `POST /logout` → invalidates `HttpSession`, deletes `JSESSIONID`, redirects `/login?logout=true` |
| COSGN00C.cbl:247–256 | `WHEN 13` → `'User not found'`; `WHEN OTHER` → `'Unable to verify the User ...'` | `AuthController.java:25–27` | `model.addAttribute("error", "Invalid user ID or password. ...")` — single error message (per OWASP; no user-enumeration split) |
| COMEN01C.cbl:75–110 | `MAIN-PARA` — first-entry / re-entry dispatch; `IF NOT CDEMO-PGM-REENTER` → `SEND-MENU-SCREEN` | `MenuController.java:39–44` | `MenuController.menu()` — `GET /menu` always renders full menu; HTTP stateless request cycle replaces COMMAREA state machine |
| COMEN01C.cbl:117–143 | `PROCESS-ENTER-KEY` — numeric option parse + admin-only guard (RULE-006): `IF CDEMO-USRTYP-USER AND OPT-USRTYPE='A' → 'No access - Admin Only option...'` | `SecurityConfig.java:62–64` | `.requestMatchers("/admin/**").hasRole("ADMIN")` — HTTP 403 replaces in-program message |
| COMEN01C.cbl:145–188 | `XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME)` — table-driven dispatch; option 11 (COPAUS0C) gated by `CICS INQUIRE` | `MenuController.java:25–37` | `MENU_OPTIONS` list of `MenuItem` records with URL strings; option 11 links to `/auth-ext/pending` (503 stub — not in PoC scope) |
| COADM01C.cbl:86–96 | `MAIN-PARA` — `IF EIBCALEN=0 → RETURN-TO-SIGNON` + SEC-009 fix: `IF NOT CDEMO-USRTYP-ADMIN → RETURN-TO-SIGNON` | `AdminMenuController.java:37` | `@PreAuthorize("hasRole('ADMIN')")` on `GET /admin/menu` — Spring method-security replaces COMMAREA role check |
| COADM01C.cbl:125–163 | `PROCESS-ENTER-KEY` — admin option dispatch; options 5–6 are `DUMMY` (not installed) | `AdminMenuController.java:27–34` | `ADMIN_OPTIONS` list; options 5–6 have `available = false` and `url = "#"` |
| COUSR00C.cbl:282–329 | `PROCESS-PAGE-FORWARD` — `STARTBR / READNEXT` loop filling 10-row screen; PF7/PF8 paging | `UserListController.java:28–31` | `userRepository.findAll()` — full list passed to Thymeleaf; no server-side paging in PoC |
| COUSR00C.cbl:189–215 | `PROCESS-ENTER-KEY` — selection flag 'U' → `XCTL COUSR02C`; 'D' → `XCTL COUSR03C` | `UserListController.java` (view) | Thymeleaf table rows render "Update" and "Delete" links to `/admin/users/update?userId=X` and `/admin/users/delete?userId=X` |
| COUSR01C.cbl:83–88 | `IF NOT CDEMO-USRTYP-ADMIN → RETURN-TO-PREV-SCREEN` (SEC-009 fix applied) | `UserAddController.java:19` | `@PreAuthorize("hasRole('ADMIN')")` on class |
| COUSR01C.cbl:121–165 | `PROCESS-ENTER-KEY` — field-by-field blank checks + `WRITE-USER-SEC-FILE` | `UserAddController.java:37–59` | `UserAddController.addUser()` — blank checks + `userRepository.existsById()` for dup + `passwordEncoder.encode()` before `save()` |
| COUSR01C.cbl:246–280 | `WRITE-USER-SEC-FILE` — `EXEC CICS WRITE DATASET('USRSEC')` + DUPKEY/DUPREC → `'User ID already exist...'` | `UserAddController.java:52–59` | `userRepository.existsById()` guard before `save()`; JPA `DataIntegrityViolationException` on PK collision if guard races |
| COUSR02C.cbl:95–100 | `IF NOT CDEMO-USRTYP-ADMIN → RETURN-TO-PREV-SCREEN` (SEC-009 fix applied) | `UserUpdateController.java:20` | `@PreAuthorize("hasRole('ADMIN')")` on class |
| COUSR02C.cbl:172–180 | `PROCESS-ENTER-KEY` after READ: `MOVE SEC-USR-PWD TO PASSWDI` line commented out (SEC-004 fix) | `UserUpdateController.java:32–38` | `updateUserForm()` — `model.addAttribute("user", u)` exposes `firstName`, `lastName`, `userType` but NOT `password`; Thymeleaf form has blank password field |
| COUSR02C.cbl:185–249 | `UPDATE-USER-INFO` — field comparison, conditional `REWRITE` USRSEC | `UserUpdateController.java:41–63` | `updateUser()` — blank `password` param → keep `existing.getPassword()`; otherwise BCrypt-encode and save |
| COUSR03C.cbl:174–192 | `DELETE-USER-INFO` — `READ USRSEC UPDATE` then `DELETE USRSEC` | `UserDeleteController.java:37–44` | `deleteUser()` — `existsById()` guard then `deleteById()` |
| COUSR03C.cbl:95 | `IF NOT CDEMO-PGM-REENTER` — first entry auto-populates user from COMMAREA | `UserDeleteController.java:28–31` | `deleteUserForm(?userId=X)` — `GET` with query param pre-populates user details from `findById()` |

---

## Deliberate Deviations from Legacy Behavior

| # | Deviation | Rationale |
|---|---|---|
| **1** | **BCrypt password hashing replaces plain-text storage and comparison (SEC-003).** COBOL stores SEC-USR-PWD as `PIC X(08)` and compares via `IF SEC-USR-PWD = WS-USER-PWD`. Java stores BCrypt hash in `UserEntity.password` and delegates matching to `BCryptPasswordEncoder`. `UserAddController` and `UserUpdateController` call `passwordEncoder.encode()` before saving. | Plain-text passwords in USRSEC is an explicit security debt documented in BUSINESS_RULES.md (RULE-004 SME note) and `analysis/CardDemo/BUSINESS_RULES.md:1025`. Existing users in USRSEC must be re-hashed at migration time. |
| **2** | **Spring Security HTTP session replaces CICS COMMAREA session model (RULE-055).** COMMAREA was a fixed-length byte array (`PIC X(01) OCCURS 1 TO 32767`) passed across every CICS XCTL. Authentication fields (`CDEMO-USER-ID`, `CDEMO-USER-TYPE`) are now held in `SecurityContext`. Navigation state is modelled in the immutable `CicsContext` record (stored in `HttpSession` when needed by downstream screens). | CICS COMMAREA has no JVM equivalent. `SecurityContext` provides thread-safe, session-scoped authentication without explicit propagation. |
| **3** | **`@PreAuthorize("hasRole('ADMIN')")` replaces in-program COMMAREA admin check (SEC-009).** COUSR01C:85–88 and COUSR02C:97–100 contained a post-commit comment marking the `IF NOT CDEMO-USRTYP-ADMIN` guard as a security fix applied during harden. COADM01C:91–96 and COUSR00C originally had no equivalent guard. Java applies the check as a declarative annotation on each controller class, enforced by Spring's method-security AOP interceptor before any handler code runs. SecurityConfig additionally protects all `/admin/**` URL paths at the filter level. | Defense-in-depth: URL-level guard prevents unauthenticated access even if a `@PreAuthorize` annotation is accidentally omitted from a new handler method. |
| **4** | **Password field not pre-filled on user update form (SEC-004).** COUSR02C:175–177 contains the commented-out line `MOVE SEC-USR-PWD TO PASSWDI OF COUSR2AI` with a note that blank `PASSWDI` now means "keep existing password". `UserUpdateController.updateUserForm()` does not pass the stored hash to the Thymeleaf model; the password `<input>` is always blank. A blank submission preserves the existing hash; a non-blank submission is BCrypt-encoded before save. | Pre-filling a password field exposes the stored value in HTML source and browser autofill. The COBOL comment explicitly records this as a security fix. |
| **5** | **BCrypt 72-character password length guard added (SEC-019 / CVE-2025-22228).** `UserAddController:48–51` and `UserUpdateController:48–50` reject passwords longer than 72 bytes with an explicit error message. | BCrypt silently truncates inputs beyond 72 bytes, making two distinct passwords effectively identical. The guard is not present in the COBOL original (passwords were limited to `PIC X(08)`) but is required by the BCrypt encoder choice made in deviation #1. |
| **6** | **CICS XCTL replaced by Spring MVC redirect.** Every `EXEC CICS XCTL PROGRAM(...)  COMMAREA(...)` becomes either an HTTP redirect (post-login, post-save) or a Thymeleaf hyperlink. No COMMAREA propagation is required because Spring Security's `SecurityContext` carries authentication for the life of the HTTP session. | XCTL is a CICS-specific mechanism with no JVM equivalent. Redirects are the standard HTTP pattern for POST-redirect-GET. |
| **7** | **Single combined error message for authentication failures.** COSGN00C returned three different messages: `'User not found. Try again ...'`, `'Wrong Password. Try again ...'`, and `'Unable to verify the User ...'`. The Java implementation emits one message (`'Invalid user ID or password. Please try again.'`) regardless of failure reason. | Distinct user-not-found vs. wrong-password messages enable user enumeration (OWASP A07). The COBOL behavior was a known security weakness; unifying the message is a deliberate improvement, not a functional regression. |

---

## What Was NOT Migrated (Dead Code / Out of Scope)

| Item | Reason |
|---|---|
| DFHBMSCA cursor-positioning (`MOVE -1 TO USERIDL OF COSGN0AI`) | Replaced by HTML `autofocus` attribute and standard browser focus behavior; the `-1` length trick is a 3270-only BMS mechanism |
| EXEC CICS ASSIGN APPLID / SYSID header fields (COSGN00C:198–203, all screens) | z/OS system identifiers; no equivalent in a Spring Boot application running on JVM/container |
| `POPULATE-HEADER-INFO` paragraph (all 7 programs) — CCDA-TITLE01/02, WS-TRANID, WS-PGMNAME, TRNNAME, PGMNAME, CURDATE, CURTIME in BMS output maps | 3270 BMS map header fields; Thymeleaf layout fragment provides application title and server timestamp without these COBOL-specific field names |
| `BmsAttr` enum (functional use) | `BmsAttr.java` is a descriptive reference stub mapping DFHBMSCA constants to named Java equivalents. No production code path dispatches on `BmsAttr` values; field protection is handled by HTML `readonly` / `type="password"` attributes in Thymeleaf templates |
| `CicsAid` enum (functional use) | `CicsAid.java` documents the DFHAID key constants for traceability. The Spring MVC layer receives standard HTTP form POSTs; there is no EIBAID evaluation at runtime |
| CICS INQUIRE PROGRAM check for COPAUS0C (COMEN01C:147–151) | Option 11 (Pending Authorization View, D9 scope) is out of PoC scope; the menu option links to `/auth-ext/pending` which returns HTTP 503 |
| CDEMO-PGM-ENTER / CDEMO-PGM-REENTER state machine (COCOM01Y:29–31 / RULE-056) | The HTTP request/response cycle makes this implicit; each GET renders the form (was ENTER), each POST processes input (was REENTER). No explicit state flag required |
| COUSR00C `STARTBR / READNEXT / READPREV` paging (10-row BMS screen, PF7/PF8) | `UserListController` calls `userRepository.findAll()`; UI paging can be added later if user count warrants it |
| COUSR00C `WS-SEND-ERASE-FLG` / `SEND-ERASE-YES` / `SEND-ERASE-NO` | BMS ERASE vs. DATAONLY distinction; no equivalent in stateless HTTP response rendering |
| COUSR03C `WS-USR-MODIFIED` flag | Set to YES in `DELETE-USER-INFO` but never read — dead within the delete flow itself |
| Admin options 5–6 (Db2 transaction-type maintenance — `COADM02Y` options 5 and 6) | No Java implementation exists; `AdminMenuController` lists them with `available = false` as explicit stubs |
| PSA / TCB memory addressing, `Z-ABEND-PROGRAM`, CICS abend paths | z/OS-only constructs; unrecoverable errors propagate as Spring MVC `@ExceptionHandler` responses |

---

## Architecture Review Findings (MEDIUM/LOW — not applied)

| # | Severity | Finding |
|---|---|---|
| M-1 | MEDIUM | `UserListController` calls `userRepository.findAll()` without pagination. A large USRSEC dataset (thousands of users) will load entirely into memory on each request. Add `Pageable` parameter and `findAll(Pageable)` before production. |
| M-2 | MEDIUM | `UserAddController.addUser()` performs `existsById()` then `save()` as two separate JPA calls without a transaction boundary — a concurrent second save between the two calls will produce a `DataIntegrityViolationException` (PK collision) rather than a graceful "User ID already exists" message. Wrap in `@Transactional` or use `saveIfAbsent` semantics. |
| M-3 | MEDIUM | `SecurityConfig.successHandler()` is an anonymous lambda (`(req, res, auth) -> {…}`). It is not tested by any unit test — only integration tests exercise it. Extract it to a named `CardDemoAuthSuccessHandler` class so it can be unit-tested and extended per role. |
| M-4 | MEDIUM | `UserUpdateController.updateUser()` does not re-validate `firstName`, `lastName`, or `userType` (non-blank, format) — these fields are validated by the COBOL `UPDATE-USER-INFO` paragraph (COUSR02C:193–217). Without server-side validation, empty strings can be persisted. Add a `@Valid` bean and `@NotBlank` annotations. |
| M-5 | MEDIUM | `CicsContext` record is documented for `HttpSession` storage but no controller in Phase 3 actually stores or retrieves it. It is a design scaffold for Phases 4/5. Document this clearly in the class Javadoc to avoid confusion. |
| N-1 | Nit | `MenuController.MENU_OPTIONS` and `AdminMenuController.ADMIN_OPTIONS` are `private static final` lists. If the option set ever needs to be configurable (e.g., by environment), the hard-coded list becomes a maintenance burden. A `@ConfigurationProperties` approach would allow overrides without recompilation. |
| N-2 | Nit | `UserDetailsServiceImpl.loadUserByUsername()` converts the input to uppercase before the repository lookup. This is correct (mirrors COBOL `FUNCTION UPPER-CASE`) but is not documented in the method body. A brief comment referencing RULE-004 would help future maintainers. |
| N-3 | Nit | `AuthController.loginPage()` accepts `@RequestParam(required = false) String error` but ignores the error value — only its presence matters. The parameter type could be `boolean`-ish (`Boolean` or using `defaultValue = "false"`). Minor readability improvement only. |

---

## SEC-009 Implementation Notes

SEC-009 (admin-role guard on user management programs) was applied as part of the harden phase and is documented via comments in the COBOL sources:

- `COUSR01C.cbl:83–88` — `*    SEC-009: EIBCALEN > 0 does not prove admin role — check COMMAREA explicitly.` followed by `IF NOT CDEMO-USRTYP-ADMIN → RETURN-TO-PREV-SCREEN`
- `COUSR02C.cbl:95–100` — identical guard with matching comment
- `COADM01C.cbl:91–96` — identical guard

The Java implementation translates this to `@PreAuthorize("hasRole('ADMIN')")` on each controller class plus the URL-level rule `.requestMatchers("/admin/**").hasRole("ADMIN")` in `SecurityConfig`. The URL-level guard is the outer defense; the method-level annotation is the inner defense. Both must pass for any admin handler to execute.

`COUSR00C` (user list) and `COUSR03C` (user delete) did not contain the harden comment in their COBOL sources but are functionally admin-only (reachable only from the admin menu). The Java controllers apply `@PreAuthorize("hasRole('ADMIN')")` consistently across all four user management controllers.

---

## Follow-ups

1. **User-to-account ownership link missing (SEC-015/016/017/018 gap).** No foreign-key relationship exists between `UserEntity` and `AccountEntity`/`CustomerEntity`. A regular user currently has access to all accounts in the system — there is no owner check in `AccountViewController` or `AccountUpdateController`. SEC-015 through SEC-018 cover account ownership scoping; these rules were not addressed in Phase 3 (authentication scope only) or Phase 4. This gap must be closed before production.

2. **COUSR01C/COUSR02C/COADM01C SEC-009 guard was applied in the harden phase.** The COBOL comments documenting the guard (`SEC-009:`) are present in the legacy source files. The Java translation carries this fix forward via declarative annotations. No additional action is required for Phase 3, but the harden phase traceability should be referenced in the Phase 3 test acceptance criteria.

3. **Option 11 — Pending Authorization View (COPAUS0C) — not in PoC scope.** The COBOL `COMEN01C` used `CICS INQUIRE PROGRAM` to detect whether COPAUS0C was installed and showed `'This option ... is not installed...'` if absent. The Java menu links to `/auth-ext/pending`; this endpoint should return HTTP 503 with an appropriate message until the authorization module (D9 scope) is implemented.

4. **Admin options 5–6 (Db2 transaction-type maintenance) are stubs.** `AdminMenuController.ADMIN_OPTIONS` entries 5 and 6 have `url = "#"` and `available = false`. These correspond to `COADM02Y` options 5 (`'COTRN00C'` — transaction type list) and 6 (`'COTRN01C'` — transaction type maintenance), both requiring a Db2 schema not present in the PoC H2 environment.

5. **USRSEC existing users must be re-hashed at migration time.** The USRSEC VSAM dataset stores plain-text `PIC X(08)` passwords. Any migration from legacy to the Spring Boot application requires a one-time password-reset or BCrypt hash pre-generation step. A migration utility is not provided by Phase 3.

6. **`DemoDataInitializer.java` seeds BCrypt hashes.** The PoC seeds users via `DemoDataInitializer` with pre-computed BCrypt hashes. This is not a migration path — it is test scaffolding only. Remove or gate behind a Spring profile before production deployment.
