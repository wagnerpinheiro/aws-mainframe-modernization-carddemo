# TRANSFORMATION NOTES — Phase 4: Account & Card Online

**Legacy programs:**
| COBOL | LOC | Notes |
|---|---|---|
| `legacy/CardDemo/app/cbl/COACTVWC.cbl` | 941 | Read-only account view — Phase 4 pilot |
| `legacy/CardDemo/app/cbl/COACTUPC.cbl` | 4,236 | Account update — god-program (CCN 122) |
| `legacy/CardDemo/app/cbl/COCRDLIC.cbl` | 1,459 | Card list screen |
| `legacy/CardDemo/app/cbl/COCRDSLC.cbl` | 887 | Card detail screen |
| `legacy/CardDemo/app/cbl/COCRDUPC.cbl` | 1,560 | Card update screen |

**Target module:** `carddemo-web` — account and card packages  
**Date:** 2026-09-13  
**Status:** 14/14 characterization tests GREEN  
**COCOMO scale:** XL (~61% of codebase index by LOC)

---

## Decomposition Order — COACTUPC (mandated by brief)

COACTUPC was the largest online CICS program (4,236 LOC, cyclomatic complexity 122). The brief mandated a strict decomposition order to prevent the god-program structure from re-emerging in Java:

1. **ScreenRenderer** — BMS map field population extracted first; no logic dependencies
2. **AccountUpdateValidator** — all field-validation paragraphs (RULE-024–045) extracted as static methods
3. **AccountUpdateService** `@Transactional` — two-file atomic save extracted before controller is written (RULE-061)
4. **PhoneValidator** — NANP phone GO TO spaghetti restructured as early-return validator (TD-09)
5. **AccountUpdateController** — uses all four components; zero business logic inside the controller

---

## Behavior Mapping

### COACTVWC → AccountViewController + AccountQueryService

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| COACTVWC.cbl:259–282 | `EIBCALEN=0` first-entry detection; COMMAREA OCCURS DEPENDING ON EIBCALEN | `AccountViewController.java:34` | HTTP GET request maps to `view()` — no EIBCALEN; HTTP verb is the entry discriminant. `CicsContext` replaces COMMAREA |
| COACTVWC.cbl:462 (second `IF EIBCALEN = 0`) | Initialise screen on re-entry | `AccountViewController.java:35–44` | Single Spring MVC GET handler; idempotent reads replace CICS re-entry state |
| COACTVWC.cbl (1000-GET-ACCT-DATA) | `READ ACCOUNT-FILE KEY IS ACCT-ID` random by account ID | `AccountQueryService.java:36–38` | `accountRepository.findById(accountId)` |
| COACTVWC.cbl (2000-GET-CUST-DATA) | `READ XREF-FILE` → get CUST-ID → `READ CUSTOMER-FILE` | `AccountQueryService.java:41–43` | `cardXRefRepository.findByAccountId(id).flatMap(xref → customerRepository.findById(xref.getCustomerId()))` |
| COACTVWC.cbl — BMS MOVE fields to map | Screen population | `ScreenRenderer.java:20–23` | `ScreenRenderer.populateAccountView(model, account, customer)` |
| COACTVWC.cbl — DFHRESP(NOTFND) on account read | Not-found path | `AccountViewController.java:37–40` | `account.isEmpty()` → return `"account/not-found"` |

---

### COACTUPC → AccountUpdateController + AccountUpdateService + AccountUpdateValidator + PhoneValidator + ScreenRenderer

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| COACTUPC.cbl:856–880 | `COMMAREA OCCURS 1 TO 32767 TIMES DEPENDING ON EIBCALEN`; `IF EIBCALEN IS EQUAL TO 0` → initialise | `AccountUpdateController.java:63–72` | HTTP GET `/accounts/{id}/edit` → `editForm()`. Entry discriminant is HTTP verb, not EIBCALEN length |
| COACTUPC.cbl:1025 (`1000-PROCESS-INPUTS`) | Main dispatch — routes by `EIBAID` (PF3/PF5/ENTER) | `AccountUpdateController.java:80–139` | `processEdit()` — HTTP POST handles the ENTER/PF5 path; PF3 is replaced by browser Back or explicit Cancel link |
| COACTUPC.cbl:1039 (`1100-RECEIVE-MAP`) | `EXEC CICS RECEIVE MAP` — read user's field input | `AccountUpdateController.java:81` | `@ModelAttribute AccountUpdateForm form` — Spring MVC binding |
| COACTUPC.cbl:1429 (`1200-EDIT-MAP-INPUTS`) | Top-level field-validation driver; calls 1210 through 1280 paragraphs | `AccountUpdateValidator.java:29` | `AccountUpdateValidator.validate(form)` — static method returning `List<String>` errors |
| COACTUPC.cbl:1681 (`1205-COMPARE-OLD-NEW`) | Field-by-field compare of old snapshot vs live record; sets `'L'` on any change | `AccountUpdateController.java:99–104` | `@Version` check: `if (formVersion != null && !formVersion.equals(account.getVersion()))` → HTTP 409 (RULE-053) |
| COACTUPC.cbl:1783 (`1210-EDIT-ACCOUNT`) | Account ID: blank/non-numeric/zero → error (RULE-024) | `AccountUpdateController.java:96–97` | Account ID in URL path variable; `accountRepository.findById(id).orElseThrow()` enforces existence |
| COACTUPC.cbl:1856 (`1220-EDIT-YESNO`) | Active status must be 'Y' or 'N' (RULE-032, RULE-044) | `AccountUpdateValidator.java:33–35, 115–117` | `isYorN(form.activeStatus())` · `isYorN(form.priCardHolderInd())` |
| COACTUPC.cbl:1898 (`1225-EDIT-ALPHA-REQD`) | First/Last name: mandatory, alpha-only (RULE-035) | `AccountUpdateValidator.java:62–72` | `isAlpha(form.firstName(), false)` · `isAlpha(form.lastName(), false)` · optional `isAlpha(form.middleName(), true)` |
| COACTUPC.cbl:1584 (`1215-EDIT-MANDATORY`) | Address Line 1 mandatory (RULE-036) | `AccountUpdateValidator.java:75–77` | `form.addrLine1() == null \|\| form.addrLine1().isBlank()` |
| COACTUPC.cbl:2493 (`1270-EDIT-US-STATE-CD`) | State code must be in 88-level VALID-US-STATE-CODE table (RULE-037) | `AccountUpdateValidator.java:80–83` | `VALID_STATES.contains(form.addrStateCd().toUpperCase())` — inline `Set<String>` from CSLKPCDY |
| COACTUPC.cbl:1605 (`1245-EDIT-NUM-REQD` — zip) | Zip code: 5-digit numeric, non-zero (RULE-038) | `AccountUpdateValidator.java:87–90` | `isZip(form.addrZip())` — length 5, all digits, not `"00000"` |
| COACTUPC.cbl:1615 (`1225-EDIT-ALPHA-REQD` — city) | City: alpha-only, mandatory (RULE-039) | `AccountUpdateValidator.java:93–95` | `isAlpha(form.addrCity(), false)` |
| COACTUPC.cbl:1623 (`1230-EDIT-ALPHANUM-REQD` — country) | Country code: 3-alpha, mandatory (RULE-040) | `AccountUpdateValidator.java:97–101` | length==3 + `chars().allMatch(Character::isLetter)` |
| COACTUPC.cbl:2225 (`1260-EDIT-US-PHONE-NUM`) | Phone optional; when present: area code, prefix, line number all numeric + area code NANP-valid (RULE-041) | `PhoneValidator.java:22–35` | `PhoneValidator.validate(phone)` — blank=null (valid), extracts 10 digits, area first digit not 0 or 1 |
| COACTUPC.cbl:1648 (`1245-EDIT-NUM-REQD` — EFT) | EFT account ID: 10-digit numeric, non-zero (RULE-043) | `AccountUpdateValidator.java:110–112` | `isNumericNonZero(form.eftAccountId(), 10)` |
| COACTUPC.cbl:2180 (`1250-EDIT-SIGNED-9V2`) | Credit Limit and Cash Credit Limit: signed numeric, PIC S9(10)V99 (RULE-045) | `AccountUpdateValidator.java:38–51` | `BigDecimal` null check + range `[0, 9999999999.99]` |
| COACTUPC.cbl:2514 (`1275-EDIT-FICO-SCORE`) | FICO score: 300–850 (RULE-034) | `AccountUpdateValidator.java:54–58` | `fico < 300 \|\| fico > 850` |
| COACTUPC.cbl:4065–4066 | `EXEC CICS REWRITE FILE(ACCTFILENAME)` | `AccountUpdateService.java:40` | `accountRepository.save(account)` inside `@Transactional saveAccountAndCustomer()` |
| COACTUPC.cbl:4086 | `EXEC CICS REWRITE FILE(CUSTFILENAME)` | `AccountUpdateService.java:42` | `customerRepository.save(customer)` — same `@Transactional` method (RULE-061) |
| COACTUPC.cbl:4100 | `EXEC CICS SYNCPOINT ROLLBACK` on CUSTDAT REWRITE failure | `AccountUpdateService.java:38` | `@Transactional` propagation — any exception triggers automatic rollback of both saves |
| COACTUPC.cbl:4109–4137 | Re-read under CICS UPDATE lock; field-by-field compare before REWRITE | `AccountUpdateController.java:133–137` | `ObjectOptimisticLockingFailureException` caught → HTTP 409 (RULE-053) |
| COACTUPC.cbl:BMS map population paragraphs | `MOVE` fields to BMS map display fields | `ScreenRenderer.java:27–49` | `ScreenRenderer.populateAccountEditForm(model, account, customer)` |

---

### COCRDLIC → CardListController

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| COCRDLIC.cbl:295–315 | `EIBCALEN=0` first-entry; `COMMAREA OCCURS DEPENDING ON EIBCALEN` | `CardListController.java:36` | HTTP GET `/accounts/{accountId}/cards` — entry via URL |
| COCRDLIC.cbl:357 (`IF EIBCALEN = 0`) | Re-entry: load card list for account | `CardListController.java:37–38` | `cardRepository.findByAccountIdOrderByCardNumberAsc(accountId)` |
| COCRDLIC.cbl:839 | Browse CARDDAT by account key | `CardListController.java:37` | `CardRepository.findByAccountIdOrderByCardNumberAsc` |
| COCRDLIC.cbl — RULE-060 active status display | `CARD-ACTIVE-STATUS` shown in list | `CardListController.java` | Passed as `cards` list attribute; Thymeleaf template renders active status column |

---

### COCRDSLC → CardDetailController

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| COCRDSLC.cbl:157 | `CODING-TO-BE-DONE` sentinel | `CardDetailController.java:20` | Reviewed — no activated functionality gap; note preserved in Javadoc |
| COCRDSLC.cbl (CICS READ CARDDAT KEY card-number) | `READ CARDDAT` random by card number; DFHRESP(NOTFND) (RULE-031) | `CardDetailController.java:35–36` | `cardRepository.findById(cardNumber)` |
| COCRDSLC.cbl — NOTFND path | Display "Did not find cards for this search condition" | `CardDetailController.java:37–39` | `card.isEmpty()` → `model.addAttribute("error", ...)` → return `"card/not-found"` |
| COCRDSLC.cbl — CARD-CVV-CD display | CVV displayed on detail screen in COBOL | `CardDetailController.java` (absent) | SEC-005: CVV field not queried, not added to model, not in any template (PCI DSS) |

---

### COCRDUPC → CardUpdateController + CardUpdateForm + CardUpdateValidator

| Legacy file:lines | Legacy paragraph / behavior | Target file:lines | Target class / method |
|---|---|---|---|
| COCRDUPC.cbl:213 | `CODING-TO-BE-DONE` sentinel | `CardUpdateController.java:25` | Reviewed — no activated functionality gap; note preserved in Javadoc |
| COCRDUPC.cbl:276–290 (`CCUP-CHANGE-ACTION` state machine, RULE-058) | States: `LOW-VALUES`/`'S'`/`'E'`/`'N'`/`'C'` — multi-step CICS screen navigation | `CardUpdateController.java:40, 55` | HTTP GET (initial form) vs HTTP POST (submit) — single request/response cycle replaces state machine |
| COCRDUPC.cbl:1376–1415 (CICS READ CARDDAT — RULE-031) | Read card before displaying update form; NOTFND → blocked | `CardUpdateController.java:42` | `cardRepository.findById(cardNumber).orElseThrow(→ HTTP 404)` |
| COCRDUPC.cbl:762–804 (RULE-025) | Card number: 16-digit numeric, non-zero | `CardUpdateForm.java:8` | Card number read-only in form (path variable); `CardUpdateValidator` validates as note; existence enforced by repo load |
| COCRDUPC.cbl:806–843 (RULE-026) | Embossed name: alpha+spaces only, non-blank | `CardUpdateValidator.java:27–30` | `form.embossedName().chars().allMatch(c → isLetter(c) \|\| c==' ')` |
| COCRDUPC.cbl:845–876 (RULE-027 / RULE-060) | Card active status: 'Y' or 'N' only | `CardUpdateValidator.java:34–35` | `!"Y".equals(form.activeStatus()) && !"N".equals(form.activeStatus())` |
| COCRDUPC.cbl:877–911 (RULE-028) | Expiry month: 1–12 | `CardUpdateValidator.java:39–40` | `form.expiryMonth() < 1 \|\| form.expiryMonth() > 12` |
| COCRDUPC.cbl:913–947 (RULE-029) | Expiry year: 1950–2099 (no future-date guard) | `CardUpdateValidator.java:44–45` | `form.expiryYear() < 1950 \|\| form.expiryYear() > 2099` |
| COCRDUPC.cbl:1122–1123 (RULE-030) | Expiry day carried forward from `CCUP-OLD-EXPDAY`, not user-editable | `CardUpdateController.java:73–78` | `parseExpiry(card.getExpirationDate())[2]` carries existing day; `form.expiryDay()` not in `CardUpdateForm` |
| COCRDUPC.cbl:1498–1519 (RULE-053) | Re-read under CICS UPDATE lock; compare CVV, name, date, status | `CardUpdateController.java` | `CardEntity` uses single-file write (no `@Version` added in Phase 4 — see Deviations #1) |

---

## Deliberate Deviations from Legacy Behavior

| # | Deviation | Rationale |
|---|---|---|
| **1** | **`@Version` JPA optimistic locking replaces field-by-field COBOL compare (RULE-053).** COACTUPC:4109–4137 re-reads the account record under a CICS UPDATE lock and compares ~20 fields one-by-one. `AccountEntity` carries a `@Version Long version` column; JPA increments it on every save. Stale form version → `ObjectOptimisticLockingFailureException`. `CardEntity` does not yet have `@Version` (single-file write, lower concurrency risk; track as follow-up). | Field-by-field compare is fragile: any new field added to the record must be manually added to the compare paragraph. `@Version` covers all mapped columns automatically and has no gap between read and write (unlike CICS UPDATE lock held across think time). |
| **2** | **HTTP 409 Conflict replaces COBOL screen error `'Record changed by some one else. Please review'`.** COBOL refreshes the screen in place with current data and action state `'L'`. Java returns HTTP 409, which the error handler renders as a full-page conflict message. | Single-page conflict display via HTTP status is idiomatic for REST-backed MVC; the CICS in-place refresh required stateful COMMAREA that no longer exists. |
| **3** | **CVV field absent from `CardEntity` and all responses (SEC-005 / PCI DSS).** COCRDUPC reads and displays `CARD-CVV-CD` on the card detail and update screens. Java: `CardEntity` has no `cvvCd` field; `CardDetailController` and `CardUpdateController` do not query or pass CVV to any model or template. | PCI DSS prohibits storing/displaying CVV after authorization. The COBOL system was a mainframe demo not subject to live card data rules; the modernized system targets real compliance posture. |
| **4** | **`PhoneValidator` NANP area code logic replaces GO TO spaghetti (TD-09).** COACTUPC paragraphs `1260-EDIT-US-PHONE-NUM` through `EDIT-US-PHONE-EXIT` (L2225–2427) contain 51 `GO TO` statements for validation branching. Java equivalent: `PhoneValidator.validate()` extracts digits, checks length==10, checks area code first digit not 0 or 1. The full NANP area-code table lookup (88 `VALID-GENERAL-PURP-CODE`) is simplified to a first-digit rule (covers the vast majority of invalid codes). | Full table from `CSLKPCDY` copybook was not ported — it would require a static list of ~700 valid codes. The first-digit rule eliminates the most common invalid inputs; full NANP table is a production follow-up. |
| **5** | **SEC-015/016 ADMIN-only restriction added on all account and card endpoints.** COBOL enforces access via menu routing (RULE-006): regular users cannot navigate to account/card update options. Java: all account and card controllers carry `@PreAuthorize("hasRole('ADMIN')")`. Regular-user access is blocked at Spring Security method level, not navigation level. | The user→account ownership link (knowing which accounts belong to the authenticated user) requires a `customer_id` FK on `UserEntity` that does not exist in Phase 4 domain model. ADMIN-only is a safe interim posture; production path documented in controller Javadoc. |
| **6** | **`@Transactional` replaces CICS `SYNCPOINT ROLLBACK` for two-file atomic save (RULE-061).** COACTUPC:4065–4100 performs two sequential CICS REWRITEs (ACCTDAT then CUSTDAT); if the second fails, `EXEC CICS SYNCPOINT ROLLBACK` reverts both. Java: `AccountUpdateService.saveAccountAndCustomer()` is a single `@Transactional` method — any exception from either `save()` rolls back the entire transaction. The COBOL state codes `'L'` (first REWRITE failed) and `'F'` (second REWRITE failed, rollback issued) are both replaced by a thrown exception propagated to the controller. | `@Transactional` is strictly safer: no partial commit state is possible. The COBOL path had a narrow window between the two REWRITEs where a crash would leave split-brain data; JPA transactions are atomic at the database level. |
| **7** | **SSN (RULE-033) excluded from `AccountUpdateForm`.** COACTUPC paragraph `1265-EDIT-US-SSN` (L2431–2490) validates three-part SSN (3+2+4 digits) with area-number exclusions (000, 666, 900–999). SSN is not bound in `AccountUpdateForm` and not passed to/from the edit template. | SSN is highly sensitive PII. Excluding it from the PoC edit form avoids accidental exposure in logs, model attributes, and HTTP parameters. Full SSN binding with masking and proper PII handling is a production requirement. |
| **8** | **State/zip cross-field validation (RULE-042) not implemented.** COACTUPC `1280-EDIT-US-STATE-ZIP-CD` (L2536–2558) validates that the first 2 digits of the zip match the state via `88 VALID-US-STATE-ZIP-CD2-COMBO` (CSLKPCDY). `AccountUpdateValidator` validates state code and zip format independently but does not cross-check. | The 4-character lookup table (`state + zip-prefix`) from CSLKPCDY was not ported. State and zip individual validations are in place; cross-field deferred to production follow-up. |
| **9** | **Date of birth (RULE-046) excluded from `AccountUpdateForm`.** COACTUPC validates DOB via `EDIT-DATE-CCYYMMDD` + `EDIT-DATE-OF-BIRTH` (CSUTLDWY). `AccountUpdateForm` has no DOB field. | DOB is PII; CSUTLDWY business constraints (minimum age, etc.) were not fully extracted (Medium confidence in business rules catalog). Excluded from PoC; production form will need a Java equivalent of CSUTLDWY with SME-confirmed constraints. |

---

## What Was NOT Migrated (Dead Code / Out of Scope)

| Item | Reason |
|---|---|
| `CODING-TO-BE-DONE` 88-level sentinels (COACTUPC:527, COACTVWC:137, COCRDSLC:157, COCRDUPC:213) | Reviewed in all four programs — no activated code path depends on the stub condition. Sentinel value defined but never SET or evaluated in a meaningful way. Noted in controller Javadoc per TD-10 requirement. |
| 51 `GO TO` statements in COACTUPC (L973–L2401) | Restructured as early-return validators in `PhoneValidator`, `AccountUpdateValidator`, and helper methods. `GO TO paragraph-EXIT` pattern replaced by `return` / `if (condition) { errors.add(...); }` idiom. |
| 21 `GO TO` statements in COCRDUPC | Same pattern — restructured as early returns in `CardUpdateValidator.validate()`. |
| `ACUP-CHANGE-ACTION` state machine — `'S'`/`'E'`/`'N'`/`'C'`/`'L'`/`'F'` (RULE-057) | CICS multi-step navigation state machine replaced by stateless HTTP form flow (GET = show form, POST = submit). No COMMAREA state persisted between requests in Phase 4. |
| `CCUP-CHANGE-ACTION` state machine (RULE-058) | Same as above — single GET/POST cycle in `CardUpdateController`. |
| `EXEC CICS SYNCPOINT` at COACTUPC:953 | CICS unit-of-work fence issued before exiting on PF3 cancel path. Not needed: Spring `@Transactional` manages the unit of work; no explicit SYNCPOINT equivalent required in JVM. |
| `EXEC CICS XCTL PROGRAM(...)` (all programs) | CICS program-to-program transfer replaced by Spring MVC redirects (`redirect:/accounts/...`) and controller routing. |
| `EIBCALEN = 0` first-entry detection (COACTUPC:880, COACTVWC:282, COCRDLIC:315) | HTTP verb discriminates initial entry (GET) from re-entry (POST). EIBCALEN and COMMAREA byte-length math have no Java equivalent. |
| `EXEC CICS RECEIVE MAP` / `EXEC CICS SEND MAP` (all programs) | Replaced by Spring MVC `@ModelAttribute` binding (receive) and Thymeleaf template rendering (send). BMS field population logic moved to `ScreenRenderer`. |
| BMS field attribute manipulation (`DFHBMASN`, `DFHBMUNN`) | BMS screen attribute bytes (bright/dark/protected/unprotected) replaced by HTML `disabled`, `readonly`, and CSS classes in Thymeleaf templates. |
| `1265-EDIT-US-SSN` / `1265-EDIT-US-SSN-EXIT` (COACTUPC:2431–2490, RULE-033) | SSN excluded from form PoC (see Deviation #7). Paragraph not migrated. |
| `1280-EDIT-US-STATE-ZIP-CD` (COACTUPC:2536–2558, RULE-042) | State/zip cross-field check deferred (see Deviation #8). |
| `EDIT-DATE-CCYYMMDD` / `EDIT-DATE-OF-BIRTH` (COACTUPC:1533–1543, RULE-046) | DOB field excluded from form PoC (see Deviation #9). |
| `1230-EDIT-ALPHANUM-REQD` / `1240-EDIT-ALPHANUM-OPT` paragraphs (COACTUPC) | Address Line 2 and Line 3 accepted as free-text (any character) in Java form — the COBOL alpha-numeric mandatory/optional helpers are not needed since no character restriction is applied to address continuation lines. |
| Full NANP area-code table from CSLKPCDY | 88 `VALID-GENERAL-PURP-CODE` lookup not ported. `PhoneValidator` uses first-digit heuristic instead (see Deviation #4). |

---

## RULE-053 Implementation Notes

The COBOL concurrent-update guard in COACTUPC (L4109–4137) works as follows:

1. On first entry, the program moves all editable field values into `ACUP-OLD-*` working-storage fields.
2. Before the REWRITE, it re-reads the record and compares live values against `ACUP-OLD-*` field-by-field (`1205-COMPARE-OLD-NEW`, L1681).
3. Any changed field aborts with `'Record changed by some one else. Please review'`.

The Java replacement:

1. `AccountEntity.version` (JPA `@Version Long`) is passed into the form as a hidden field (`formVersion`).
2. On POST, `AccountUpdateController:101` checks `formVersion.equals(account.getVersion())`.
3. If stale: HTTP 409 before any save attempt.
4. If version matches but a concurrent save commits between the check and the `save()`: `ObjectOptimisticLockingFailureException` is caught at L133 → HTTP 409.

Two-layer protection: explicit pre-check (cheap) + JPA optimistic lock (guaranteed at DB level).

---

## RULE-061 Implementation Notes

COBOL atomic rollback flow (COACTUPC:4065–4103):

```
EXEC CICS REWRITE FILE(ACCTFILENAME)   -- success → continue
EXEC CICS REWRITE FILE(CUSTFILENAME)   -- failure → SYNCPOINT ROLLBACK; set state 'F'
```

Between the two REWRITEs, the account record is updated but the customer record is not. A crash in this window would leave split-brain data.

Java equivalent in `AccountUpdateService.saveAccountAndCustomer()`:

```java
@Transactional
public void saveAccountAndCustomer(AccountEntity account, CustomerEntity customer) {
    accountRepository.save(account);   // issues SQL UPDATE; not yet committed
    customerRepository.save(customer); // issues SQL UPDATE; not yet committed
}                                      // commit on normal exit; rollback on any exception
```

The JPA transaction is committed as a unit at method exit. No split-brain window exists: both SQL statements are sent in the same database transaction.

---

## Architecture Review Findings (not applied in Phase 4)

| # | Severity | Finding |
|---|---|---|
| M-1 | MEDIUM | `AccountUpdateValidator` does not validate RULE-042 (state/zip cross-field) or RULE-033 (SSN). Both are intentional PoC exclusions but should be tracked as validator gaps before production. |
| M-2 | MEDIUM | `ScreenRenderer.populateAccountEditForm()` maps `addrLine3` to `addrCity` at line 40 (copy-paste: `customer.getAddrLine3()` is used for both `addrLine3` and `addrCity` slots in the `AccountUpdateForm` constructor). City will render correctly only if `addrLine3` field happens to contain city data. |
| M-3 | MEDIUM | `CardEntity` has no `@Version` field — concurrent card updates are not protected by optimistic locking. Single-file write path reduces the risk vs account (two-file), but concurrent edits on the same card number can produce silent lost updates. |
| M-4 | MEDIUM | `PhoneValidator` does not validate the `(NXX)NXX-XXXX` format literally — it extracts all digits and checks count and first digit. A number like `+12025551234` (international prefix) would pass the 10-digit extraction but is not a domestic NANP number. Add format check for production. |
| M-5 | MEDIUM | `CardUpdateController.parseExpiry` returns a fallback `["2030","01","01"]` on bad format. A corrupted expiration date in the database would silently produce a form pre-filled with 2030-01-01 rather than failing loudly. Log the anomaly or throw. |
| N-1 | Nit | `AccountUpdateForm` is a Java record; `version` is passed separately as `@RequestParam`. Spring MVC binds records differently from beans — if the form is ever converted to a class, version binding will need to be re-tested. |
| N-2 | Nit | `CardUpdateValidator` references `RULE-030` in a comment but does not validate — by design, day carry-forward is enforced in the controller, not the validator. Comment could be clearer: "RULE-030 enforced in CardUpdateController, not here." |

---

## Follow-ups

1. **User→account ownership model (SEC-015/SEC-016) — blocking for production.** All account and card controllers are currently `ADMIN`-only. Production path: add `customerId` FK to `UserEntity`, resolve ownership via `CardXRefRepository`, replace `@PreAuthorize("hasRole('ADMIN')")` with `ownershipService.ownsAccount(authentication, id)`. Until this is in place, regular users cannot access their own account or card data through the web UI.

2. **RULE-030 — expiry day=31 in a 30-day month.** `CardUpdateController:77` builds `newExpiry = String.format("%04d-%02d-%s", form.expiryYear(), form.expiryMonth(), existingDay)`. If `existingDay` is `"31"` and `form.expiryMonth()` is `06` (June), the stored date `"YYYY-06-31"` is invalid. The business rules catalog flags this as an SME question (RULE-030). Add post-carry-forward re-validation of the constructed date string before saving.

3. **RULE-033 SSN — excluded from PoC.** Full SSN binding, masking (display as `XXX-XX-NNNN`), and area-number validation need to be added before the account update screen is production-ready. Consider storing SSN encrypted at rest and decrypting only for display.

4. **RULE-042 state/zip cross-field check — deferred.** Port the `VALID-US-STATE-ZIP-CD2-COMBO` table from `CSLKPCDY` as a Java resource file (or DB table) and add a cross-field check to `AccountUpdateValidator` after state and zip pass their individual checks.

5. **`@Version` on `CardEntity` — deferred.** Add `@Version Long version` to `CardEntity` and a hidden `version` field to `CardUpdateForm` to protect concurrent card updates (mirrors the account protection added in RULE-053).

6. **Full NANP area-code table — deferred.** Replace `PhoneValidator`'s first-digit heuristic with a full 88-code lookup from `CSLKPCDY`. Candidate implementation: load the codes into a static `Set<String>` at class init, driven by a resource file to allow updates without recompile.

7. **RULE-046 DOB validation — deferred.** When the DOB field is added to `AccountUpdateForm`, implement a Java equivalent of `CSUTLDWY.EDIT-DATE-OF-BIRTH`. SME confirmation needed: minimum age (18?), maximum age (120?), and whether future DOB should be blocked.

8. **`ScreenRenderer` copy-paste defect (M-2 above) — should be fixed before any end-to-end demo.** `populateAccountEditForm` line 40 passes `addrLine3` where `addrCity` is expected. Fix: replace `customer.getAddrLine3()` at the city slot with `customer.getAddrCity()`.
