# CardDemo Business Rules Catalog

> **Extraction method:** Method B (3 parallel subagent passes — Calculations, Validations, Lifecycle).  
> **Source verified:** Key citations spot-checked against live source files before writing.  
> **Coverage:** 106 COBOL source files across 4 sub-applications.

---

## Summary Table

| ID | Rule Name | Category | Priority | Source | Confidence |
|----|-----------|----------|----------|--------|------------|
| RULE-001 | Sign-on: User ID must not be blank | Authentication | P0 | COSGN00C.cbl:117-130 | High |
| RULE-002 | Sign-on: Password must not be blank | Authentication | P0 | COSGN00C.cbl:123-130 | High |
| RULE-003 | Sign-on: User must exist in USRSEC | Authentication | P0 | COSGN00C.cbl:209-256 | High |
| RULE-004 | Sign-on: Password match (input uppercased) | Authentication | P0 | COSGN00C.cbl:132-136, 223-246 | High |
| RULE-005 | User type routing: Admin vs. Regular | Authentication | P0 | COSGN00C.cbl:230-239 | High |
| RULE-006 | Regular user blocked from admin-only options | Authentication | P0 | COMEN01C.cbl:136-143 | High |
| RULE-007 | Monthly interest charge formula | Calculation | P0 | CBACT04C.cbl:462-468 | High |
| RULE-008 | Account balance updated after interest run | Calculation | P0 | CBACT04C.cbl:350-356 | High |
| RULE-009 | Cycle credit/debit accumulators reset after interest | Calculation | P0 | CBACT04C.cbl:353-354 | High |
| RULE-010 | Bill payment amount equals full current balance | Calculation | P0 | COBIL00C.cbl:220-234 | High |
| RULE-011 | Zero-balance payment blocked | Calculation | P0 | COBIL00C.cbl:198-204 | High |
| RULE-012 | Transaction ID sequential generation | Calculation | P0 | COBIL00C.cbl:212-219 | High |
| RULE-013 | Authorization expiry: Julian-day complement arithmetic | Calculation | P0 | CBPAUP0C.cbl:280-285 | High |
| RULE-014 | Expired auth: count and amount decremented | Calculation | P0 | CBPAUP0C.cbl:287-293 | High |
| RULE-015 | Auth summary deletion threshold — **DEFECT** | Calculation | P0 | CBPAUP0C.cbl:156-158 | High |
| RULE-016 | Interest rate lookup with DEFAULT group fallback | Calculation | P1 | CBACT04C.cbl:415-439 | High |
| RULE-017 | Interest skipped when rate is zero | Calculation | P1 | CBACT04C.cbl:213-217 | High |
| RULE-018 | Interest transaction hardcoded type/category codes | Calculation | P1 | CBACT04C.cbl:482-490 | High |
| RULE-019 | Authorization expiry default threshold (5 days) | Calculation | P1 | CBPAUP0C.cbl:196-200 | High |
| RULE-020 | Statement total expenditure accumulation | Calculation | P2 | CBSTM03A.CBL:325, 429, 433-434 | High |
| RULE-021 | Transaction report three-level totaling | Calculation | P2 | CBTRN03C.cbl:287-310 | High |
| RULE-022 | Report page size = 20 lines | Calculation | P2 | CBTRN03C.cbl:131-132 | High |
| RULE-023 | Transaction date range filter | Calculation | P1 | CBTRN03C.cbl:173-177 | High |
| RULE-024 | Account ID: 11-digit numeric, non-zero | Validation | P1 | COCRDUPC.cbl:721-756; COACTUPC.cbl:1783-1818 | High |
| RULE-025 | Card number: 16-digit numeric, non-zero | Validation | P1 | COCRDUPC.cbl:762-804 | High |
| RULE-026 | Card embossed name: alphabets and spaces only | Validation | P1 | COCRDUPC.cbl:806-843 | High |
| RULE-027 | Card active status: Y or N | Validation | P1 | COCRDUPC.cbl:845-876 | High |
| RULE-028 | Card expiry month: 1–12 | Validation | P1 | COCRDUPC.cbl:877-911 | High |
| RULE-029 | Card expiry year: 1950–2099 | Validation | P1 | COCRDUPC.cbl:913-947 | High |
| RULE-030 | Card expiry day: not user-editable, carried forward | Validation | P1 | COCRDUPC.cbl:1122-1123 | High |
| RULE-031 | Card must exist in CARDDAT before update | Validation | P1 | COCRDUPC.cbl:1376-1415 | High |
| RULE-032 | Account active status: Y or N | Validation | P1 | COACTUPC.cbl:1472-1476 | High |
| RULE-033 | SSN: 3-2-4 format, area number exclusions | Validation | P1 | COACTUPC.cbl:121-123, 2431-2490 | High |
| RULE-034 | FICO credit score: 300–850 | Validation | P1 | COACTUPC.cbl:848-849, 2514-2529 | High |
| RULE-035 | Customer name: alphabets only, max 25 chars | Validation | P1 | COACTUPC.cbl:1560-1582 | High |
| RULE-036 | Address Line 1: mandatory | Validation | P1 | COACTUPC.cbl:1584-1590 | High |
| RULE-037 | US state code: valid 2-alpha state | Validation | P1 | COACTUPC.cbl:1592-1602, 2493-2513 | High |
| RULE-038 | Zip code: 5-digit numeric, non-zero | Validation | P1 | COACTUPC.cbl:1605-1611 | High |
| RULE-039 | City: alphabets only | Validation | P1 | COACTUPC.cbl:1615-1621 | High |
| RULE-040 | Country code: 3-alpha | Validation | P1 | COACTUPC.cbl:1623-1630 | High |
| RULE-041 | US phone number: NANP-valid area code | Validation | P1 | COACTUPC.cbl:2225-2430 | High |
| RULE-042 | State/Zip cross-field: prefix must match state | Validation | P1 | COACTUPC.cbl:1664-1669, 2536-2560 | High |
| RULE-043 | EFT account ID: 10-digit numeric, non-zero | Validation | P1 | COACTUPC.cbl:1648-1655 | High |
| RULE-044 | Primary cardholder indicator: Y or N | Validation | P1 | COACTUPC.cbl:1657-1662 | High |
| RULE-045 | Credit and cash credit limits: signed numeric | Validation | P1 | COACTUPC.cbl:1484-1501, 2180-2219 | High |
| RULE-046 | Date of birth: valid CCYYMMDD + DOB-specific check | Validation | P1 | COACTUPC.cbl:1533-1543 | Medium |
| RULE-047 | Transaction add: account or card must be supplied | Validation | P1 | COTRN02C.cbl:195-229 | High |
| RULE-048 | Transaction add: all fields required | Validation | P1 | COTRN02C.cbl:251-319 | High |
| RULE-049 | Transaction amount: ±99999999.99 format | Validation | P1 | COTRN02C.cbl:339-351 | High |
| RULE-050 | Transaction date: YYYY-MM-DD, valid calendar date | Validation | P1 | COTRN02C.cbl:353-427 | High |
| RULE-051 | Transaction confirmation: Y required to commit | Validation | P1 | COTRN02C.cbl:169-188 | High |
| RULE-052 | Duplicate transaction ID rejected | Validation | P1 | COTRN02C.cbl:735-741 | High |
| RULE-053 | Optimistic locking: concurrent update prevention | Validation | P0 | COACTUPC.cbl:4109-4137; COCRDUPC.cbl:1498-1519 | High |
| RULE-054 | Date validation: error code 2513 silently suppressed | Validation | P2 | COTRN02C.cbl:397-427 | High |
| RULE-055 | User session lifecycle | Lifecycle | P0 | COSGN00C.cbl, COMEN01C.cbl, COADM01C.cbl | High |
| RULE-056 | Program context state: enter vs. re-enter | Lifecycle | P2 | COCOM01Y.cpy:29-31 | High |
| RULE-057 | Account update workflow state machine | Lifecycle | P1 | COACTUPC.cbl:654-668 | High |
| RULE-058 | Card update workflow state machine | Lifecycle | P1 | COCRDUPC.cbl:276-290 | High |
| RULE-059 | Account active status domain | Lifecycle | P0 | CVACT01Y.cpy:6; COACTUPC.cbl:192-195 | High |
| RULE-060 | Card active status domain | Lifecycle | P0 | CVACT02Y.cpy:10; COCRDUPC.cbl:69-72 | High |
| RULE-061 | Atomic rollback on two-file account update failure | Lifecycle | P0 | COACTUPC.cbl:4065-4103 | High |
| RULE-062 | Authorization response: '00' = Approved, else Declined | Lifecycle | P1 | COPAUS0C.cbl:536-539 | Medium |
| RULE-063 | Batch: card must exist in XREF — reject code 100 | Batch Posting | P0 | CBTRN02C.cbl:380-392 | High |
| RULE-064 | Batch: account must exist in ACCTDAT — reject code 101 | Batch Posting | P0 | CBTRN02C.cbl:393-399 | High |
| RULE-065 | Batch: credit limit enforcement — reject code 102 | Batch Posting | P0 | CBTRN02C.cbl:403-413 | High |
| RULE-066 | Batch: account not expired — reject code 103 | Batch Posting | P0 | CBTRN02C.cbl:414-420 | High |
| RULE-067 | Batch: positive amount → credit, negative → debit | Batch Posting | P1 | CBTRN02C.cbl:547-552 | High |
| RULE-068 | Batch: transaction category balance accumulation | Batch Posting | P0 | CBTRN02C.cbl:508, 527 | High |
| RULE-069 | Batch: account balance updated per posted transaction | Batch Posting | P0 | CBTRN02C.cbl:547-560 | High |

**Totals:** 69 rules — 28 P0 · 36 P1 · 5 P2 | 7 SME questions | 1 confirmed defect (RULE-015)

---

## Rules by Category

---

## Authentication & Authorization

---

### RULE-001: Sign-On — User ID Must Not Be Blank
**Category:** Authentication
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COSGN00C.cbl:117-130`
**Plain English:** Before any file lookup, the system verifies the User ID field is not all spaces or LOW-VALUES. A blank ID is rejected immediately.
**Specification:**
  Given a user on the sign-on screen
  When the User ID field is blank or LOW-VALUES
  Then display `'Please enter User ID ...'`, position cursor at User ID field, re-display screen. No file access attempted.
**Parameters:** User ID length = PIC X(08); USRSEC dataset name = `'USRSEC  '`
**Edge cases handled:** Both SPACES and LOW-VALUES trigger blank check.
**Confidence:** High

---

### RULE-002: Sign-On — Password Must Not Be Blank
**Category:** Authentication
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COSGN00C.cbl:123-130`
**Plain English:** After the User ID passes blank check, the password must also be non-blank.
**Specification:**
  Given a non-blank User ID
  When the Password field is blank or LOW-VALUES
  Then display `'Please enter Password ...'`, no file lookup attempted.
**Parameters:** Password length = PIC X(08)
**Confidence:** High

---

### RULE-003: Sign-On — User Must Exist in USRSEC File
**Category:** Authentication
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COSGN00C.cbl:209-256`
**Plain English:** A CICS READ on USRSEC keyed by User ID (8 chars) must succeed. CICS RESP=13 (NOTFND) means the user does not exist.
**Specification:**
  Given non-blank User ID and Password
  When CICS READ USRSEC with key = User ID returns RESP=13
  Then display `'User not found. Try again ...'`, re-display sign-on.
  When RESP=0 (normal)
  Then proceed to password comparison (RULE-004).
  When RESP=any other non-zero value
  Then display `'Unable to verify the User ...'`
**Parameters:** Dataset = `'USRSEC  '`; CICS RESP 13 = NOTFND
**Confidence:** High

---

### RULE-004: Sign-On — Password Must Match Stored Value
**Category:** Authentication
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COSGN00C.cbl:132-136, 223-246`
**Plain English:** Both User ID and Password are uppercased before comparison. The uppercased password is compared character-for-character against SEC-USR-PWD. Passwords are stored as plain text (no hashing).
**Specification:**
  Given user types `'jsmith'` / `'pass1234'`
  When system converts to upper-case and compares `'PASS1234'` to SEC-USR-PWD
  Then if unequal, display `'Wrong Password. Try again ...'`, cursor at Password field.
**Parameters:** Input uppercased via FUNCTION UPPER-CASE; SEC-USR-PWD = PIC X(08)
**Edge cases handled:** Mixed-case passwords stored in USRSEC will only match if entered as uppercase. **SME should confirm intended case sensitivity.**
**Confidence:** High

---

### RULE-005: User Type Routing — Admin vs. Regular
**Category:** Authentication
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COSGN00C.cbl:227-240`; `legacy/CardDemo/app/cpy/COCOM01Y.cpy:27-28`
**Plain English:** After successful authentication, SEC-USR-TYPE determines which menu the user enters. Admin ('A') → COADM01C; all others → COMEN01C. This type persists in COMMAREA for the full session.
**Specification:**
  Given authentication succeeds
  When SEC-USR-TYPE = 'A'
  Then XCTL to program `COADM01C` (6 admin menu options including user management and transaction-type maintenance)
  When SEC-USR-TYPE ≠ 'A' (implied 'U')
  Then XCTL to program `COMEN01C` (regular user menu)
**Parameters:** Admin type = `'A'`; Regular type = `'U'`; Admin option count = 6 (COADM02Y)
**Confidence:** High

---

### RULE-006: Regular User Blocked from Admin-Only Menu Options
**Category:** Authentication
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COMEN01C.cbl:136-143`
**Plain English:** Each regular-menu option carries a user-type indicator. If a regular user ('U') selects an option marked admin-only ('A'), access is blocked.
**Specification:**
  Given a session with CDEMO-USER-TYPE = 'U'
  When the user selects menu option N where CDEMO-MENU-OPT-USRTYPE(N) = 'A'
  Then display `'No access - Admin Only option...'`, remain on menu.
**Parameters:** All 11 current regular-user menu options are marked 'U'; guard exists but is latent.
**Confidence:** High

---

## Calculation

---

### RULE-007: Monthly Interest Charge Formula
**Category:** Calculation
**Priority:** P0 — moves money
**Source:** `legacy/CardDemo/app/cbl/CBACT04C.cbl:462-468`
**Plain English:** Each month, interest is charged per transaction category by multiplying the category's accumulated balance by the annual rate and dividing by 1200. No ROUNDED clause — result is truncated toward zero.
**Specification:**
  Given a transaction category balance (TRAN-CAT-BAL) and an annual rate (DIS-INT-RATE) for that category
  When the monthly interest batch (CBACT04C) runs
  Then WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200
  And WS-MONTHLY-INT is accumulated into WS-TOTAL-INT for the account
  And an interest transaction record is written (type '01', category '05')
**Parameters:**
  - `DIS-INT-RATE` = PIC S9(04)V99 — annual percentage (e.g., 18.00 = 18%)
  - Divisor = `1200` (hardcoded: converts annual % to monthly decimal × 100)
  - `TRAN-CAT-BAL` = PIC S9(09)V99
  - `WS-MONTHLY-INT` = PIC S9(09)V99, **no ROUNDED clause** → truncates
**Edge cases handled:** Zero-rate categories are skipped entirely (see RULE-017). Category with no rate record uses DEFAULT group (see RULE-016).
**Suspected defect:** Truncation (not rounding) of fractional cents could accumulate material error across millions of accounts. Rewrite must make a deliberate rounding decision.
**Confidence:** High

---

### RULE-008: Account Balance Updated After Interest Run
**Category:** Calculation
**Priority:** P0 — moves money
**Source:** `legacy/CardDemo/app/cbl/CBACT04C.cbl:350-356`
**Plain English:** After all category interest is summed, the total is added to ACCT-CURR-BAL and the account record is rewritten. Cycle accumulators are also reset (see RULE-009).
**Specification:**
  Given WS-TOTAL-INT = sum of all category monthly interest for account
  When 1050-UPDATE-ACCOUNT runs
  Then ACCT-CURR-BAL += WS-TOTAL-INT; REWRITE ACCTDAT.
**Parameters:** `ACCT-CURR-BAL` = PIC S9(10)V99
**Confidence:** High

---

### RULE-009: Cycle Credit/Debit Accumulators Reset After Interest
**Category:** Calculation
**Priority:** P0 — resets the base for next cycle's credit limit check (RULE-065)
**Source:** `legacy/CardDemo/app/cbl/CBACT04C.cbl:353-354`
**Plain English:** After posting interest, both ACCT-CURR-CYC-CREDIT and ACCT-CURR-CYC-DEBIT are set to zero unconditionally. This starts fresh accumulators for the next billing cycle.
**Specification:**
  Given any account processed by the interest batch
  When 1050-UPDATE-ACCOUNT runs
  Then ACCT-CURR-CYC-CREDIT = 0 and ACCT-CURR-CYC-DEBIT = 0.
**Edge cases handled:** Reset occurs even if no interest was charged (zero-rate account or zero balance).
**Confidence:** High

---

### RULE-010: Bill Payment Amount Equals Full Current Balance
**Category:** Calculation
**Priority:** P0 — moves money
**Source:** `legacy/CardDemo/app/cbl/COBIL00C.cbl:220-234`
**Plain English:** Bill payment is always a full pay-off. The payment amount is set to the current balance at the moment of reading, and the balance is then computed to zero. No partial payment path exists.
**Specification:**
  Given ACCT-CURR-BAL = $452.75 and user confirms with 'Y'
  When PROCESS-ENTER-KEY executes
  Then TRAN-AMT = $452.75 (full balance)
  And ACCT-CURR-BAL = $452.75 − $452.75 = $0.00
  And a transaction is written with type '02', category '02', source 'POS TERM', desc `'BILL PAYMENT - ONLINE'`, merchant ID 999999999, name `'BILL PAYMENT'`, city/zip `'N/A'`.
**Parameters (all hardcoded):**
  - TRAN-TYPE-CD = `'02'`; TRAN-CAT-CD = `2`; TRAN-SOURCE = `'POS TERM'`
  - TRAN-MERCHANT-ID = `999999999`; TRAN-MERCHANT-NAME = `'BILL PAYMENT'`
  - TRAN-MERCHANT-CITY = `'N/A'`; TRAN-MERCHANT-ZIP = `'N/A'`
**Edge cases handled:** Zero-balance guard (RULE-011). DUPKEY on transaction write shows error, no retry.
**Confidence:** High

---

### RULE-011: Zero-Balance Payment Blocked
**Category:** Calculation
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COBIL00C.cbl:198-204`
**Plain English:** If the account's current balance is zero or negative, the payment is blocked before any file update is attempted.
**Specification:**
  Given ACCT-CURR-BAL ≤ 0
  When user attempts bill payment
  Then display `'You have nothing to pay...'`; no transaction written.
**Confidence:** High

---

### RULE-012: Transaction ID Sequential Generation
**Category:** Calculation
**Priority:** P0 — data integrity; duplicate prevention
**Source:** `legacy/CardDemo/app/cbl/COBIL00C.cbl:212-219`; `legacy/CardDemo/app/cbl/COTRN02C.cbl:735-741`
**Plain English:** The next transaction ID is derived by browsing the TRANSACT file backwards to find the highest existing ID and adding 1. No locking serializes this read-increment-write sequence.
**Specification:**
  Given TRANSACT file has highest TRAN-ID = '0000000000000045'
  When a bill payment or transaction add is processed
  Then new TRAN-ID = '0000000000000046'
  Given TRANSACT file is empty
  Then TRAN-ID = ZEROS; new ID = 1.
**Parameters:** TRAN-ID = PIC X(16); WS-TRAN-ID-NUM = PIC 9(16)
**Edge cases handled:** DUPKEY/DUPREC on WRITE shows `'Tran ID already exist...'` to user; no automatic retry.
**Suspected defect:** Two concurrent bill-payment or transaction-add sessions can derive the same ID. No serialization mechanism exists (no CICS ENQUEUE or DB2 sequence). Second writer will receive DUPREC error and must restart manually.
**Confidence:** High

---

### RULE-013: Authorization Expiry — Julian-Day Complement Arithmetic
**Category:** Calculation
**Priority:** P0 — governs deletion of financial authorization records
**Source:** `legacy/CardDemo/app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl:280-285`
**Plain English:** The age of each pending authorization is computed using a complement-encoded Julian day. The stored date PA-AUTH-DATE-9C is `99999 − actual-YYDDD`. Elapsed days = current YYDDD minus reversed value.
**Specification:**
  Given PA-AUTH-DATE-9C = 99985 (meaning actual day 14)
  And CURRENT-YYDDD = 00021 (day 21)
  When 4000-CHECK-IF-EXPIRED evaluates
  Then WS-AUTH-DATE = 99999 − 99985 = 14
  And WS-DAY-DIFF = 21 − 14 = 7
  And if WS-EXPIRY-DAYS ≤ 7, authorization is qualified for deletion.
**Parameters:**
  - Complement constant = `99999` (hardcoded)
  - Year encoding = 2-digit year YYDDD (ACCEPT FROM DAY)
  - `WS-EXPIRY-DAYS` = configurable via SYSIN (default 5, see RULE-019)
**Suspected defect:** Year rollover from YY=99 to YY=00 (year 2099→2100) produces a large negative WS-DAY-DIFF, preventing cleanup. Latent Y2K-style century-boundary defect.
**Confidence:** High

---

### RULE-014: Expired Authorization — Count and Amount Decremented
**Category:** Calculation
**Priority:** P0 — keeps authorization summary balances accurate
**Source:** `legacy/CardDemo/app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl:287-293`
**Plain English:** When an authorization detail is qualified for deletion, the summary counters and amounts are decremented. Approved auths (resp-code '00') reduce the approved count and approved amount; declined auths reduce the declined count and declined amount.
**Specification:**
  Given an expired auth with PA-AUTH-RESP-CODE = '00'
  When 4000-CHECK-IF-EXPIRED qualifies it for deletion
  Then PA-APPROVED-AUTH-CNT -= 1; PA-APPROVED-AUTH-AMT -= PA-APPROVED-AMT.
  Given resp-code ≠ '00' (declined)
  Then PA-DECLINED-AUTH-CNT -= 1; PA-DECLINED-AUTH-AMT -= PA-TRANSACTION-AMT.
**Confidence:** High

---

### RULE-015: Authorization Summary Deletion Threshold — **CONFIRMED DEFECT**
**Category:** Calculation
**Priority:** P0
**Source:** `legacy/CardDemo/app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl:156-158`
**Plain English:** After processing all detail records, if the summary is "empty" it should be deleted. The condition tests both approved and declined counts reaching zero — but the code contains a copy-paste defect: both sides of the AND test `PA-APPROVED-AUTH-CNT`, so declined count is never checked.
**Specification (as coded):**
  `IF PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0` ← second operand should be PA-DECLINED-AUTH-CNT
  Given PA-APPROVED-AUTH-CNT = 0, PA-DECLINED-AUTH-CNT = 2
  When loop ends
  Then summary IS deleted (because the second condition is also testing the approved count)
  — declined auths orphaned.
**Intended behaviour (inferred):**
  Given PA-APPROVED-AUTH-CNT = 0 AND PA-DECLINED-AUTH-CNT = 0
  Then delete the summary segment.
**SME question:** Confirm intended delete condition. Fix requires changing second operand to `PA-DECLINED-AUTH-CNT`.
**Confidence:** High (defect confirmed by reading source at line 156)

---

### RULE-016: Interest Rate Lookup with DEFAULT Group Fallback
**Category:** Calculation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/CBACT04C.cbl:415-439`
**Plain English:** The interest rate is looked up from DISCGRP keyed on (account group ID + transaction type + category). If no record exists for the account's group, the lookup is retried with group ID `'DEFAULT'`.
**Specification:**
  Given account in group 'GOLD' with no DISCGRP record for type '01'/category 0001
  When 1200-GET-INTEREST-RATE is called
  Then set FD-DIS-ACCT-GROUP-ID = `'DEFAULT'` and re-read DISCGRP.
  Given DEFAULT group also has no record
  Then program abends (9999-ABEND-PROGRAM).
**Parameters:** Default group literal = `'DEFAULT'` (padded to 10 chars); DISCGRP not-found status = `'23'`
**Confidence:** High

---

### RULE-017: Interest Skipped When Rate Is Zero
**Category:** Calculation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/CBACT04C.cbl:213-217`
**Plain English:** If DIS-INT-RATE = 0.00 for a category (e.g., promotional 0% APR), interest and fee computation are skipped entirely. No interest transaction is written.
**Specification:**
  Given DIS-INT-RATE = 0.00 for a category
  When CBACT04C processes that category balance
  Then skip both 1300-COMPUTE-INTEREST and 1400-COMPUTE-FEES.
**Confidence:** High

---

### RULE-018: Interest Transaction Hardcoded Type/Category/Source
**Category:** Calculation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/CBACT04C.cbl:482-490`
**Plain English:** Every interest charge transaction is written with hardcoded classification. The description is built as `'Int. for a/c ' + account-ID`.
**Parameters (all hardcoded):**
  - TRAN-TYPE-CD = `'01'`; TRAN-CAT-CD = `'05'`; TRAN-SOURCE = `'System'`
  - TRAN-MERCHANT-ID = `0`
  - Description prefix = `'Int. for a/c '`
**Confidence:** High

---

### RULE-019: Authorization Expiry Default Threshold = 5 Days
**Category:** Calculation
**Priority:** P1
**Source:** `legacy/CardDemo/app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl:196-200`
**Plain English:** The number of days before an authorization expires is read from the SYSIN parameter P-EXPIRY-DAYS. If not provided or non-numeric, the default is 5 days.
**Specification:**
  Given SYSIN contains non-numeric or empty P-EXPIRY-DAYS
  When 1000-INITIALIZE runs
  Then WS-EXPIRY-DAYS = 5 (hardcoded default).
**Parameters:** Default = `5` days; valid if P-EXPIRY-DAYS IS NUMERIC
**Confidence:** High

---

### RULE-020: Statement Total Expenditure
**Category:** Calculation
**Priority:** P2
**Source:** `legacy/CardDemo/app/cbl/CBSTM03A.CBL:325, 429, 433-434`
**Plain English:** The statement total (ST-TOTAL-TRAMT) is the algebraic sum of all transaction amounts for a card number within the statement run. Accumulator is reset to zero at the start of each new account.
**Parameters:** `WS-TOTAL-AMT` = PIC S9(9)V99 COMP-3; display field = PIC Z(9).99−
**Confidence:** High

---

### RULE-021: Transaction Report Three-Level Totaling
**Category:** Calculation
**Priority:** P2
**Source:** `legacy/CardDemo/app/cbl/CBTRN03C.cbl:287-310`
**Plain English:** The transaction detail report maintains page, account, and grand totals. Each transaction amount adds to both page and account totals. Page total rolls to grand total at page break and is reset. Account total is reset on new card number.
**Confidence:** High

---

### RULE-022: Report Page Size = 20 Lines
**Category:** Calculation
**Priority:** P2
**Source:** `legacy/CardDemo/app/cbl/CBTRN03C.cbl:131-132`
**Plain English:** A new page starts every 20 transaction detail lines (FUNCTION MOD(line counter, 20) = 0).
**Parameters:** `WS-PAGE-SIZE` = PIC 9(03) COMP-3 VALUE `20` (hardcoded)
**Confidence:** High

---

### RULE-023: Transaction Date Range Filter
**Category:** Calculation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/CBTRN03C.cbl:173-177`
**Plain English:** The report includes only transactions whose process timestamp falls within [WS-START-DATE, WS-END-DATE] inclusive. Dates come from a DATEPARM flat file (format: `'YYYY-MM-DD YYYY-MM-DD'`).
**Specification:**
  Given WS-START-DATE = '2025-01-01', WS-END-DATE = '2025-01-31'
  When TRAN-PROC-TS(1:10) = '2025-01-15'
  Then include. When '2025-02-01', exclude.
**Confidence:** High

---

## Validation

---

### RULE-024: Account ID — 11-Digit Numeric, Non-Zero
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:721-756`; `legacy/CardDemo/app/cbl/COACTUPC.cbl:1783-1818`
**Plain English:** Account ID must be present, all-numeric, exactly 11 digits, and not all zeros.
**Specification:**
  Given Account ID = blank or LOW-VALUES → error `'Account number not provided'`
  Given Account ID contains non-numeric → error `'Account Number if supplied must be a 11 digit Non-Zero Number'`
  Given Account ID = '00000000000' → same error.
**Parameters:** Field = PIC X(11) display / PIC 9(11) stored (CC-ACCT-ID-N)
**Confidence:** High

---

### RULE-025: Card Number — 16-Digit Numeric, Non-Zero
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:762-804`
**Plain English:** Card number must be 16 numeric digits, non-blank, non-zero.
**Parameters:** Field = PIC X(16); numeric check via IS NOT NUMERIC; CC-CARD-NUM-N = PIC 9(16)
**Confidence:** High

---

### RULE-026: Card Embossed Name — Alphabets and Spaces Only
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:806-843`
**Plain English:** Card name (CCUP-NEW-CRDNAME) must be non-blank and contain only A–Z, a–z, and spaces. Digits, punctuation, or special characters are rejected.
**Specification:**
  Given name = 'JOHN O BRIEN' → accept.
  Given name = 'JOHN2 SMITH' → `'Card name can only contain alphabets and spaces'`.
  Given name = blank → `'Card name not provided'`.
**Parameters:** Field = PIC X(50); check via INSPECT CONVERTING alpha chars to spaces → result must be all spaces.
**Confidence:** High

---

### RULE-027: Card Active Status — Y or N
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:845-876`
**Plain English:** Card active/inactive flag must be exactly 'Y' or 'N'. Any other character is invalid.
**Parameters:** 88 FLG-YES-NO-VALID VALUES 'Y', 'N'; error = `'Card Active Status must be Y or N'`
**Confidence:** High

---

### RULE-028: Card Expiry Month — 1–12
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:877-911`
**Plain English:** Expiry month must be two-digit numeric in range 1–12 inclusive.
**Parameters:** 88 VALID-MONTH VALUES 1 THRU 12; error = `'Card expiry month must be between 1 and 12'`
**Confidence:** High

---

### RULE-029: Card Expiry Year — 1950–2099
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:913-947`
**Plain English:** Expiry year must be 4-digit numeric in range 1950–2099. The system does NOT validate that the expiry date is in the future.
**Parameters:** 88 VALID-YEAR VALUES 1950 THRU 2099; error = `'Invalid card expiry year'`
**Edge cases handled:** Past expiry years can be saved online; the batch program (RULE-066) enforces expiry at transaction posting time.
**SME question:** Should the online card update screen reject expiry dates already in the past?
**Confidence:** High

---

### RULE-030: Card Expiry Day — Not User-Editable, Carried Forward
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:1122-1123`
**Plain English:** The day portion of the card expiry date is always copied from the existing record (CCUP-OLD-EXPDAY) regardless of any month/year change. The day is frozen at its original value.
**Edge cases handled:** If the original expiry day is 31 and the user changes the month to a 30-day month, the stored date `'YYYY-MM-31'` would be invalid. No validation prevents this.
**SME question:** Is the day always intended to remain fixed? Should it be re-validated against the new month?
**Confidence:** High

---

### RULE-031: Card Must Exist in CARDDAT Before Update
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:1376-1415`
**Plain English:** Before displaying card detail for update, the system reads CARDDAT keyed by card number. NOTFND results in a blocked search.
**Specification:**
  Given card number not in CARDDAT
  When CICS READ returns DFHRESP(NOTFND)
  Then display `'Did not find cards for this search condition'`.
**Parameters:** Dataset = `'CARDDAT '`; key = WS-CARD-RID-CARDNUM PIC X(16)
**Confidence:** High

---

### RULE-032: Account Active Status — Y or N
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1472-1476, 1856-1893`
**Plain English:** Account active/inactive flag must be exactly 'Y' or 'N'. Blank, '0', or any other value is rejected.
**Parameters:** 88 FLG-ACCT-STATUS-ISVALID VALUES 'Y', 'N'; generic editor 1220-EDIT-YESNO
**Confidence:** High

---

### RULE-033: SSN — 3-2-4 Format with Area Number Exclusions
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:121-123, 2431-2490`
**Plain English:** SSN is entered in three separate fields (3+2+4 digits). Each part must be numeric and non-zero. Part 1 additionally cannot be 000, 666, or 900–999 (IRS-invalid area numbers).
**Specification:**
  Given Part1 = '666' → `'SSN: First 3 chars: should not be 000, 666, or between 900 and 999'`
  Given Part2 = '00' or Part3 = '0000' → `'[field] must not be zero.'`
**Parameters:** 88 INVALID-SSN-PART1 VALUES 0, 666, 900 THRU 999; stored as PIC 9(09)
**Confidence:** High

---

### RULE-034: FICO Credit Score — 300–850
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:848-849, 2514-2529`
**Plain English:** FICO score must be a 3-digit integer in range 300–850 inclusive.
**Parameters:** 88 FICO-RANGE-IS-VALID VALUES 300 THROUGH 850; error = `'FICO Score: should be between 300 and 850'`
**Confidence:** High

---

### RULE-035: Customer Name — Alphabets Only, Max 25 Characters
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1560-1582, 1898-1951`
**Plain English:** First Name and Last Name are mandatory alpha-only fields (max 25 chars). Middle Name follows same alpha rule but is optional (blank = valid).
**Parameters:** 1230-EDIT-ALPHA (mandatory); 1235-EDIT-ALPHA-OPT (optional); check via INSPECT CONVERTING
**Confidence:** High

---

### RULE-036: Address Line 1 — Mandatory
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1584-1590`
**Plain English:** Address Line 1 must be non-blank. Any character is allowed (no format restriction).
**Confidence:** High

---

### RULE-037: US State Code — Valid 2-Alpha State
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1592-1602, 2493-2513`
**Plain English:** State code must be exactly 2 alphabetic characters and must appear in the 88-level valid-state-code table (CSLKPCDY copybook, all 50 US states + territories).
**Parameters:** 88 VALID-US-STATE-CODE in CSLKPCDY; error = `'[State]: is not a valid state code'`
**Confidence:** High

---

### RULE-038: Zip Code — 5-Digit Numeric, Non-Zero
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1605-1611`
**Plain English:** Zip code must be 5 numeric digits, non-blank, non-zero.
**Confidence:** High

---

### RULE-039: City — Alphabets Only
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1615-1621`
**Plain English:** City must be non-blank and contain only A–Z / a–z characters.
**Confidence:** High

---

### RULE-040: Country Code — 3-Alpha
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1623-1630`
**Plain English:** Country code must be exactly 3 alphabetic characters, non-blank.
**Confidence:** High

---

### RULE-041: US Phone Number — NANP-Valid Area Code
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:2225-2430`
**Plain English:** Phone numbers are stored as `(NXX)NXX-XXXX`. Area code (3 digits) must be in the NANP valid-code table (CSLKPCDY). Prefix and line number must be numeric non-zero. Phone is optional — all-blank passes.
**Specification:**
  Given area code = '000' → `'[Phone Number 1]: Area code cannot be zero'`
  Given area code = '999' → `'[Phone Number 1]: Not valid North America general purpose area code'`
  Given all parts blank → accept.
**Parameters:** 88 VALID-GENERAL-PURP-CODE in CSLKPCDY; stored as PIC X(15) format `(NXX)NXX-XXXX  `
**Confidence:** High

---

### RULE-042: State/Zip Cross-Field — Prefix Must Match State
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1664-1669, 2536-2560`
**Plain English:** After state and zip individually pass, the first 2 digits of the zip must be valid for the state. The 4-character key (state code + zip prefix) is looked up in 88 VALID-US-STATE-ZIP-CD2-COMBO (CSLKPCDY).
**Specification:**
  Given State = 'TX', Zip = '90210' → 'TX90' not in table → `'Invalid zip code for state'`; both fields marked in error.
**Confidence:** High

---

### RULE-043: EFT Account ID — 10-Digit Numeric, Non-Zero
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1648-1655`
**Plain English:** EFT account ID must be 10 numeric digits, non-blank, non-zero.
**Confidence:** High

---

### RULE-044: Primary Cardholder Indicator — Y or N
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1657-1662`
**Plain English:** Primary card holder indicator must be exactly 'Y' or 'N'.
**Confidence:** High

---

### RULE-045: Credit and Cash Credit Limits — Signed Numeric with 2 Decimal Places
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1484-1501, 2180-2219`
**Plain English:** Credit Limit and Cash Credit Limit must pass FUNCTION TEST-NUMVAL-C (accepts signed values with optional currency symbols and decimal points). Also applies to Current Balance, Current Cycle Credit, and Current Cycle Debit.
**Parameters:** Stored as PIC S9(10)V99; display as PIC +ZZZ,ZZZ,ZZZ.99
**Confidence:** High

---

### RULE-046: Date of Birth — Valid CCYYMMDD + DOB-Specific Check
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:1533-1543`
**Plain English:** Date of birth passes two-stage validation: first a general CCYYMMDD calendar check (EDIT-DATE-CCYYMMDD), then a DOB-specific check (EDIT-DATE-OF-BIRTH in CSUTLDWY utility).
**SME question:** What are the DOB-specific constraints in EDIT-DATE-OF-BIRTH — minimum age, maximum age, or other? The utility content determines the full rule.
**Confidence:** Medium — two-stage validation confirmed; DOB-specific constraints require CSUTLDWY review.

---

### RULE-047: Transaction Add — Account or Card Must Be Supplied
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COTRN02C.cbl:195-229`
**Plain English:** When adding a transaction, the user must supply either an Account ID or a Card Number. Both blank is rejected. Supplied value is validated against XREF to look up the other.
**Specification:**
  Given both fields blank → `'Account or Card Number must be entered...'`
  Given Account ID present → look up in CXACAIX (account→card XREF); NOTFND → `'Account ID NOT found...'`
  Given Card Number present → look up in CCXREF; NOTFND → `'Card Number NOT found...'`
**Confidence:** High

---

### RULE-048: Transaction Add — All Required Fields
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COTRN02C.cbl:251-319`
**Plain English:** Transaction type, category, source, description, amount, original date, process date, and merchant information are all required.
**Confidence:** High

---

### RULE-049: Transaction Amount — ±99999999.99 Format
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COTRN02C.cbl:339-351`
**Plain English:** Amount must be exactly 11 characters: position 1 = '+' or '−'; positions 2–9 numeric; position 10 = '.'; positions 11–12 numeric cents.
**Specification:**
  Given '+00050025.99' → accept.
  Given '50025.99' (no sign) → `'Amount should be in format -99999999.99'`
**Parameters:** Input field = PIC +99999999.99; stored as PIC S9(09)V99
**Confidence:** High

---

### RULE-050: Transaction Date — YYYY-MM-DD, Valid Calendar Date
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COTRN02C.cbl:353-427`
**Plain English:** Both Original Date and Process Date must be in YYYY-MM-DD format. After format check, each is passed to the CSUTLDTC external date utility for calendar validation.
**Specification:**
  Given '2024-02-29' on a leap year → CSUTLDTC returns '0000' → accept.
  Given '2024-02-30' → CSUTLDTC returns non-zero (not '2513') → `'Orig Date - Not a valid date...'`
**Parameters:** Validation utility = `CSUTLDTC`; valid severity = `'0000'`; error '2513' suppressed (see RULE-054)
**Confidence:** High

---

### RULE-051: Transaction Confirmation — Y Required to Commit
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COTRN02C.cbl:169-188`
**Plain English:** Even after all field validations pass, the user must enter 'Y' (or 'y') in the Confirm field to write the transaction. 'N', blank, or other leaves the screen open.
**Specification:**
  Given CONFIRMI = 'Y' or 'y' → call ADD-TRANSACTION, write to TRANSACT.
  Given CONFIRMI = 'N', 'n', spaces → display `'Confirm to add this transaction...'`, no write.
  Given any other value → `'Invalid value. Valid values are (Y/N)...'`
**Confidence:** High

---

### RULE-052: Duplicate Transaction ID Rejected
**Category:** Validation
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COTRN02C.cbl:735-741`
**Plain English:** When writing a new transaction, CICS WRITE returning DUPKEY or DUPREC is caught and shown to the user. No automatic retry.
**Parameters:** Error = `'Tran ID already exist...'`
**Confidence:** High

---

### RULE-053: Optimistic Locking — Concurrent Update Prevention
**Category:** Validation
**Priority:** P0 — prevents silent lost updates
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:4109-4137`; `legacy/CardDemo/app/cbl/COCRDUPC.cbl:1498-1519`
**Plain English:** When the operator confirms a save, the program re-reads the record under a CICS UPDATE lock and compares all editable fields against the snapshot taken when the screen was first populated. Any changed field aborts the update and refreshes with current data.
**Specification:**
  Given operator A reads account at T1, operator B saves at T2, operator A saves at T3
  When comparison at T3 detects ACCT-CURR-BAL changed
  Then display `'Record changed by some one else. Please review'`; refresh with T2 data; abort.
**Fields compared (account):** ACCT-ACTIVE-STATUS, ACCT-CURR-BAL, ACCT-CREDIT-LIMIT, ACCT-CASH-CREDIT-LIMIT, ACCT-CURR-CYC-CREDIT, ACCT-CURR-CYC-DEBIT, all date fields — plus full CUSTOMER-RECORD field-by-field.
**Fields compared (card):** CARD-CVV-CD, CARD-EMBOSSED-NAME, CARD-EXPIRAION-DATE (year, month, day), CARD-ACTIVE-STATUS.
**Edge cases handled:** CICS UPDATE lock held from READ to REWRITE; long think time = lock held until CICS timeout.
**Confidence:** High

---

### RULE-054: Date Validation — Error Code 2513 Silently Suppressed
**Category:** Validation
**Priority:** P2 — anomaly
**Source:** `legacy/CardDemo/app/cbl/COTRN02C.cbl:397-407, 417-427`
**Plain English:** When CSUTLDTC returns a non-zero severity with message number '2513', the date is NOT rejected. This error code is intentionally or accidentally tolerated.
**Specification:**
  Given CSUTLDTC severity = '0001', message number = '2513'
  When check reads `IF CSUTLDTC-RESULT-MSG-NUM NOT = '2513'`
  Then the date is accepted as if valid.
**SME question:** What is CSUTLDTC message 2513 and why is it suppressed for both Orig Date and Proc Date? Possible explanations: leap-year ambiguity, timezone warning, or accidental suppression.
**Confidence:** High (code unambiguous); business intent = Low.

---

## Lifecycle

---

### RULE-055: User Session Lifecycle
**Category:** Lifecycle
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/COSGN00C.cbl:80-257`; `legacy/CardDemo/app/cbl/COMEN01C.cbl`; `legacy/CardDemo/app/cbl/COADM01C.cbl`
**Plain English:** A session begins when CICS transaction CC00 invokes COSGN00C with no COMMAREA (EIBCALEN=0). After successful authentication, control transfers to the appropriate menu. PF3 from any menu returns to COSGN00C, which re-initializes COMMAREA on next entry.
**States:**

| State | Trigger | Destination |
|-------|---------|-------------|
| Unauthenticated | EIBCALEN=0 | Sign-on screen |
| Auth failed — user not found | USRSEC RESP=13 | Re-display sign-on |
| Auth failed — wrong password | Password mismatch | Re-display sign-on |
| Authenticated (admin) | SEC-USR-TYPE='A' | COADM01C |
| Authenticated (user) | SEC-USR-TYPE='U' | COMEN01C |
| Signed off | PF3 from any menu | Sign-on screen |

**Parameters:** Passwords stored plain-text; no session timeout in COBOL code (CICS handles timeout).
**Confidence:** High

---

### RULE-056: Program Context State — Enter vs. Re-Enter
**Category:** Lifecycle
**Priority:** P2
**Source:** `legacy/CardDemo/app/cpy/COCOM01Y.cpy:29-31`
**Plain English:** Every CICS program uses CDEMO-PGM-CONTEXT (PIC 9(01)) in the COMMAREA to distinguish first entry (0 = CDEMO-PGM-ENTER) from returning entry (1 = CDEMO-PGM-REENTER). First entry initializes the screen; returning entry processes user input.
**Confidence:** High

---

### RULE-057: Account Update Workflow State Machine
**Category:** Lifecycle
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:654-668`
**Plain English:** COACTUPC tracks multi-step update workflow via ACUP-CHANGE-ACTION (PIC X(01)) in COMMAREA. PF5 is required to commit; PF3 issues SYNCPOINT before exiting.

| Code | Meaning |
|------|---------|
| LOW-VALUES | Nothing loaded |
| 'S' | Data displayed, accepting edits |
| 'E' | Validation errors shown |
| 'N' | Edits valid, awaiting PF5 |
| 'C' | Successfully committed |
| 'L' | Lock failed |
| 'F' | Locked but REWRITE failed |

**Confidence:** High

---

### RULE-058: Card Update Workflow State Machine
**Category:** Lifecycle
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/COCRDUPC.cbl:276-290`
**Plain English:** Identical state machine to RULE-057, applied to CARD-RECORD via CCUP-CHANGE-ACTION. Writes only to CARDDAT (single file), so no two-file rollback scenario. On completion, returns to COCRDLIC (Card List) rather than to the main menu.
**Confidence:** High

---

### RULE-059: Account Active Status Domain
**Category:** Lifecycle
**Priority:** P0
**Source:** `legacy/CardDemo/app/cpy/CVACT01Y.cpy:6`; `legacy/CardDemo/app/cbl/COACTUPC.cbl:192-195`
**Plain English:** ACCT-ACTIVE-STATUS accepts only 'Y' (active) or 'N' (inactive). No terminal state — either value can be changed to the other. The online update enforces this; batch transaction posting does NOT check this field.
**SME question:** Should an inactive account ('N') block batch transaction posting? The current code does not enforce this, but the field implies it should.
**Confidence:** High (validation); Medium (intended business meaning of 'N')

---

### RULE-060: Card Active Status Domain
**Category:** Lifecycle
**Priority:** P0
**Source:** `legacy/CardDemo/app/cpy/CVACT02Y.cpy:10`; `legacy/CardDemo/app/cbl/COCRDUPC.cbl:69-72`
**Plain English:** CARD-ACTIVE-STATUS accepts only 'Y' or 'N'. No terminal state. Batch transaction processing uses the XREF and account record — it does not check CARD-ACTIVE-STATUS.
**Confidence:** High

---

### RULE-061: Atomic Rollback on Two-File Account Update Failure
**Category:** Lifecycle
**Priority:** P0 — prevents account/customer record split-brain
**Source:** `legacy/CardDemo/app/cbl/COACTUPC.cbl:4065-4103`
**Plain English:** Account update performs two sequential CICS REWRITEs (ACCTDAT then CUSTDAT). If the second REWRITE fails, EXEC CICS SYNCPOINT ROLLBACK undoes both changes.
**Specification:**
  Given REWRITE ACCTDAT succeeds, REWRITE CUSTDAT fails
  When COACTUPC detects non-zero RESP on CUSTDAT REWRITE
  Then EXEC CICS SYNCPOINT ROLLBACK; both files reverted; state set to 'F'.
  Given REWRITE ACCTDAT fails (first file)
  Then state set to 'L'; no ROLLBACK needed (nothing committed).
**Confidence:** High

---

### RULE-062: Authorization Response — '00' = Approved, Else Declined
**Category:** Lifecycle
**Priority:** P1
**Source:** `legacy/CardDemo/app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl:536-539`
**Plain English:** The authorization view program maps PA-AUTH-RESP-CODE to a display character: '00' → 'A' (Approved); any other code → 'D' (Declined).
**SME question:** What is the full list of valid non-'00' response codes and their business meanings? The authorization processing program CBPAUP0C was not fully analyzed.
**Confidence:** Medium — display translation confirmed; full response-code taxonomy not enumerated.

---

## Batch Posting

---

### RULE-063: Batch — Card Must Exist in XREF (Reject Code 100)
**Category:** Batch Posting
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/CBTRN02C.cbl:380-392`
**Plain English:** Each daily transaction's card number is looked up in XREF-FILE. If not found, the transaction is written to the DALYREJS reject file with reason code 100.
**Specification:**
  Given DALYTRAN-CARD-NUM not in XREF-FILE (INVALID KEY)
  Then WS-VALIDATION-FAIL-REASON = 100; desc = `'INVALID CARD NUMBER FOUND'`; write to DALYREJS.
**Parameters:** Reject code 100; Dataset = XREF-FILE (VSAM KSDS keyed PIC X(16))
**Confidence:** High

---

### RULE-064: Batch — Account Must Exist in ACCTDAT (Reject Code 101)
**Category:** Batch Posting
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/CBTRN02C.cbl:393-399`
**Plain English:** After finding the card in XREF, the account ID from XREF is used to read ACCOUNT-FILE. If the account is not found, the transaction is rejected.
**Parameters:** Reject code 101; desc = `'ACCOUNT RECORD NOT FOUND'`
**Confidence:** High

---

### RULE-065: Batch — Credit Limit Enforcement (Reject Code 102)
**Category:** Batch Posting
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/CBTRN02C.cbl:403-413`
**Plain English:** A prospective balance is computed and compared to the credit limit. If it exceeds the limit, the transaction is rejected.
**Specification:**
  Given ACCT-CREDIT-LIMIT = $5,000, ACCT-CURR-CYC-CREDIT = $3,000, ACCT-CURR-CYC-DEBIT = −$500, DALYTRAN-AMT = +$2,600
  When WS-TEMP-BAL = $3,000 − (−$500) + $2,600 = $5,100
  And $5,000 < $5,100
  Then reject with code 102, desc = `'OVERLIMIT TRANSACTION'`.
**Formula:** `WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT − ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT`
**Edge cases handled:** Negative (payment) amounts can never trigger overlimit alone.
**SME question:** The formula uses cycle-to-date amounts, not the full running balance. Does this correctly reflect the credit policy? Prior-cycle carry-forward balances are excluded from the overlimit check.
**Confidence:** High

---

### RULE-066: Batch — Account Not Expired at Transaction Origin Date (Reject Code 103)
**Category:** Batch Posting
**Priority:** P0
**Source:** `legacy/CardDemo/app/cbl/CBTRN02C.cbl:414-420`
**Plain English:** ACCT-EXPIRAION-DATE (PIC X(10), format 'CCYY-MM-DD') must be ≥ the first 10 characters of DALYTRAN-ORIG-TS. Comparison is lexicographic string comparison.
**Specification:**
  Given ACCT-EXPIRAION-DATE = '2024-12-31', DALYTRAN-ORIG-TS(1:10) = '2025-01-05'
  Then '2024-12-31' < '2025-01-05' → reject code 103, desc = `'TRANSACTION RECEIVED AFTER ACCT EXPIRATION'`.
**Edge cases handled:** String comparison works correctly only when both fields use YYYY-MM-DD format.
**SME question:** Confirm ACCT-EXPIRAION-DATE and DALYTRAN-ORIG-TS are always YYYY-MM-DD format. Format inconsistency would silently produce wrong results.
**Confidence:** High (logic); Medium (format consistency assumption).

---

### RULE-067: Batch — Positive Amount → Credit Accumulator; Negative → Debit Accumulator
**Category:** Batch Posting
**Priority:** P1
**Source:** `legacy/CardDemo/app/cbl/CBTRN02C.cbl:547-552`
**Plain English:** The transaction amount is always added to ACCT-CURR-BAL. Additionally, positive amounts also accumulate in ACCT-CURR-CYC-CREDIT; negative amounts in ACCT-CURR-CYC-DEBIT.
**Specification:**
  Given DALYTRAN-AMT = +$150 → ADD to ACCT-CURR-BAL; ADD to ACCT-CURR-CYC-CREDIT.
  Given DALYTRAN-AMT = −$50 → ADD (−$50) to ACCT-CURR-BAL (reduces it); ADD to ACCT-CURR-CYC-DEBIT.
**Confidence:** High

---

### RULE-068: Batch — Transaction Category Balance Accumulation
**Category:** Batch Posting
**Priority:** P0 — feeds RULE-007 (interest calculation)
**Source:** `legacy/CardDemo/app/cbl/CBTRN02C.cbl:508, 527`
**Plain English:** Every posted transaction amount is accumulated into TRAN-CAT-BAL-RECORD keyed on (account ID + transaction type code + transaction category code). A new record is created if none exists.
**Specification:**
  Given TRAN-CAT-BAL for (12345678901, '01', 0001) = $200; DALYTRAN-AMT = +$50
  When 2700-UPDATE-TCATBAL runs
  Then TRAN-CAT-BAL = $250.00; record rewritten to TCATBAL.
**Confidence:** High

---

### RULE-069: Batch — Account Balance Updated per Posted Transaction
**Category:** Batch Posting
**Priority:** P0 — primary financial ledger update
**Source:** `legacy/CardDemo/app/cbl/CBTRN02C.cbl:547-560`
**Plain English:** For each validated transaction: account balance is updated (RULE-067), category balance is updated (RULE-068), and the transaction record is written permanently to the TRANSACT file.
**Side-effects:**
  1. ACCOUNT-FILE REWRITE (balance update)
  2. TCATBAL-FILE WRITE or REWRITE (category balance)
  3. TRANSACT-FILE WRITE (permanent ledger record)
**Confidence:** High

---

## Rules Requiring SME Confirmation

| Rule | Priority | SME Question |
|------|----------|-------------|
| RULE-004 | P0 | Passwords in USRSEC are stored plain-text (no hashing) and input is uppercased before comparison — confirm this is the intended behavior and assess security posture. |
| RULE-015 | P0 | **Confirmed defect**: auth summary deletion tests `PA-APPROVED-AUTH-CNT` on both sides of AND. Should second operand be `PA-DECLINED-AUTH-CNT`? Fix before migration. |
| RULE-029 | P1 | Should the online card update screen reject expiry dates already in the past? Currently no such guard exists online. |
| RULE-030 | P1 | Card expiry day is frozen at original value even when month/year changes — is this intentional? Could result in invalid stored dates (e.g., day 31 in a 30-day month). |
| RULE-046 | P1 | What DOB-specific constraints does EDIT-DATE-OF-BIRTH implement in CSUTLDWY? (minimum age, maximum age, or other?) |
| RULE-054 | P2 | What is CSUTLDTC message number 2513 and why is it suppressed in transaction date validation? Possible defect — unknown if intentional. |
| RULE-059 | P0 | Should account active-status 'N' block batch transaction posting? The batch (CBTRN02C) does not check ACCT-ACTIVE-STATUS before posting. |
| RULE-062 | P1 | What are all valid values for PA-AUTH-RESP-CODE and their business meanings in the authorization processing module (CBPAUP0C)? |
| RULE-065 | P0 | The overlimit check uses only cycle-to-date amounts, ignoring carry-forward balances from prior cycles. Is this the intended credit policy? |
| RULE-066 | P0 | Confirm ACCT-EXPIRAION-DATE and DALYTRAN-ORIG-TS are always stored in YYYY-MM-DD format. A format mismatch silently produces wrong expiry comparisons. |

---

## Cross-Cutting Notes for Modernization Team

**No rounding rule for interest (RULE-007):** The COMPUTE at CBACT04C:465 has no ROUNDED clause — COBOL truncates toward zero. A 1-cent rounding error per category per month could accumulate materially across large account volumes. The rewrite must make a deliberate rounding choice (truncate, half-up, or banker's rounding) and document it.

**Fee calculation is an unimplemented stub:** `1400-COMPUTE-FEES` in CBACT04C contains only `EXIT`. No fee business rules exist anywhere in the codebase. The rewrite team must design fee logic from scratch or obtain requirements from product owners.

**No partial payment:** COBIL00C always pays the full balance. There is no minimum payment calculation, no minimum payment due derivation, and no partial payment path anywhere in the codebase.

**Plain-text password storage:** USRSEC stores passwords as PIC X(08) plain text. The rewrite must introduce proper credential hashing.

**Concurrent transaction ID generation (RULE-012):** The sequential READPREV+increment pattern is not serialized. Any production rewrite must use a database sequence, an atomic counter, or a generated UUID.

**2-digit-year Julian date in authorization (RULE-013):** CBPAUP0C uses `ACCEPT CURRENT-YYDDD FROM DAY` (2-digit year). Year rollover from 99→00 (year 2099→2100) would break authorization cleanup. Modernized system must use 4-digit years.

**Interest rate encoding:** DIS-INT-RATE is PIC S9(04)V99 — a value of `1800` means 18.00%. Division by 1200 (= 100×12) converts to a monthly decimal rate. Document this encoding convention carefully in the modern data model.
