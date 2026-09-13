# CardDemo Data Objects Catalog

> Derived from copybooks and verified against program usage. Record lengths from copybook comments.

---

## Summary

| Entity | Copybook | Storage | Record Length | Key | Consumed By | Produced By |
|--------|----------|---------|---------------|-----|-------------|-------------|
| Account | CVACT01Y.cpy | ACCTDAT (VSAM KSDS) | 300 B | ACCT-ID (PIC 9(11)) | COACTUPC, COACTVWC, CBTRN02C, CBACT04C, COBIL00C | COACTUPC, CBTRN02C, CBACT04C, COBIL00C |
| Card | CVACT02Y.cpy | CARDDAT (VSAM KSDS) | 150 B | CARD-NUM (PIC X(16)) | COCRDUPC, COCRDLIC, COCRDSLC, CBTRN02C | COCRDUPC |
| Customer | CUSTREC.cpy / CVCUS01Y.cpy | CUSTDAT (VSAM KSDS) | 500 B | CUST-ID (PIC 9(09)) | COACTUPC, COACTVWC, CBCUS01C | COACTUPC, CBIMPORT |
| Card-Account XREF | CVACT03Y.cpy | XREF-FILE / CCXREF / CXACAIX (VSAM AIX) | 50 B | XREF-CARD-NUM (PIC X(16)) | CBTRN02C, COTRN02C, COBIL00C | CBIMPORT |
| Transaction | CVTRA05Y.cpy | TRANSACT (VSAM KSDS) | 350 B | TRAN-ID (PIC X(16)) | COTRN00C, COTRN01C, CBTRN03C, CBSTM03A | COTRN02C, COBIL00C, CBACT04C, CBTRN02C |
| Daily Transaction (input) | CVTRA06Y.cpy | DALYTRAN (sequential) | — | seq | CBTRN01C, CBTRN02C | External feed |
| Transaction Category Balance | CVTRA01Y.cpy | TCATBAL (VSAM KSDS) | 50 B | TRAN-CAT-KEY (17 B) | CBACT04C | CBTRN02C, CBACT04C |
| Disclosure / Interest Rate Group | CVTRA02Y.cpy | DISCGRP (VSAM KSDS) | 50 B | DIS-GROUP-KEY (16 B) | CBACT04C | Reference data — maintained externally |
| User Security Record | CSUSR01Y.cpy | USRSEC (VSAM KSDS) | 80 B | SEC-USR-ID (PIC X(08)) | COSGN00C | COUSR01C, COUSR02C |
| Authorization Summary (IMS) | CIPAUSMY.cpy | IMS DB (PCB PASFLPCB) | — | Card/account composite | COPAUS0C, CBPAUP0C | CBPAUP0C |
| Authorization Detail (IMS) | CIPAUDTY.cpy | IMS DB (PCB PADFLPCB) | — | PA-AUTH-DATE-9C + child | COPAUS0C, CBPAUP0C | External authorization module |
| Authorization Query/Reply (MQ) | CCPAURQY.cpy / CCPAURLY.cpy | IBM MQ | — | — | CBPAUP0C | External channel |

---

## Entity Detail

---

### ACCOUNT-RECORD
**Copybook:** `legacy/CardDemo/app/cpy/CVACT01Y.cpy`
**Storage:** ACCTDAT — VSAM KSDS, record length 300 bytes
**Primary key:** ACCT-ID PIC 9(11)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| ACCT-ID | PIC 9(11) | Account identifier — 11-digit numeric; primary key |
| ACCT-ACTIVE-STATUS | PIC X(01) | 'Y' = active; 'N' = inactive. Validated online; NOT checked by batch posting (see RULE-059 SME question). |
| ACCT-CURR-BAL | PIC S9(10)V99 | Current running balance. Updated by every posted transaction and by monthly interest run. |
| ACCT-CREDIT-LIMIT | PIC S9(10)V99 | Total credit limit. Used in batch overlimit check (RULE-065). |
| ACCT-CASH-CREDIT-LIMIT | PIC S9(10)V99 | Cash advance credit limit. Stored but NOT enforced by batch posting — cash limit gate is absent. |
| ACCT-OPEN-DATE | PIC X(10) | Account open date; format 'CCYY-MM-DD'. |
| ACCT-EXPIRAION-DATE | PIC X(10) | Account expiration date; format 'CCYY-MM-DD'. Used in batch reject (RULE-066). Note: field name misspells "EXPIRATION". |
| ACCT-REISSUE-DATE | PIC X(10) | Date account was reissued; format 'CCYY-MM-DD'. |
| ACCT-CURR-CYC-CREDIT | PIC S9(10)V99 | Cycle-to-date credit accumulator (purchases and charges). Reset to 0 by monthly interest run (RULE-009). |
| ACCT-CURR-CYC-DEBIT | PIC S9(10)V99 | Cycle-to-date debit accumulator (payments). Stored as negative values. Reset to 0 by monthly interest run. |
| ACCT-ADDR-ZIP | PIC X(10) | ZIP code associated with account (used for fraud-detection reference). |
| ACCT-GROUP-ID | PIC X(10) | Links account to a disclosure/interest-rate group (key into DISCGRP). |
| FILLER | PIC X(178) | Reserved. |

**Rules consuming this object:** RULE-007, RULE-008, RULE-009, RULE-053, RULE-057, RULE-059, RULE-061, RULE-063 through RULE-069

---

### CARD-RECORD
**Copybook:** `legacy/CardDemo/app/cpy/CVACT02Y.cpy`
**Storage:** CARDDAT — VSAM KSDS, record length 150 bytes
**Primary key:** CARD-NUM PIC X(16)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| CARD-NUM | PIC X(16) | Card number — 16-character (not validated as Luhn by the system). Primary key. |
| CARD-ACCT-ID | PIC 9(11) | Parent account ID. |
| CARD-CVV-CD | PIC 9(03) | Card Verification Value (3-digit code). Stored plain — NOT masked in any visible output or log. |
| CARD-EMBOSSED-NAME | PIC X(50) | Name printed on card. Alphabets + spaces only (RULE-026). |
| CARD-EXPIRAION-DATE | PIC X(10) | Card expiration date 'CCYY-MM-DD'. Month 1-12 (RULE-028); year 1950-2099 (RULE-029); day not user-editable (RULE-030). Note: field name misspells "EXPIRATION". |
| CARD-ACTIVE-STATUS | PIC X(01) | 'Y' = active; 'N' = inactive. Validated by COCRDUPC; not checked by batch posting. |
| FILLER | PIC X(59) | Reserved. |

**Rules consuming this object:** RULE-025 through RULE-031, RULE-053, RULE-058, RULE-060

---

### CUSTOMER-RECORD
**Copybook:** `legacy/CardDemo/app/cpy/CUSTREC.cpy`
**Storage:** CUSTDAT — VSAM KSDS, record length 500 bytes
**Primary key:** CUST-ID PIC 9(09)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| CUST-ID | PIC 9(09) | Customer identifier — 9-digit numeric. |
| CUST-FIRST-NAME | PIC X(25) | First name; mandatory, alphabets only (RULE-035). |
| CUST-MIDDLE-NAME | PIC X(25) | Middle name; optional, alphabets only. |
| CUST-LAST-NAME | PIC X(25) | Last name; mandatory, alphabets only (RULE-035). |
| CUST-ADDR-LINE-1 | PIC X(50) | Address line 1; mandatory, any character (RULE-036). |
| CUST-ADDR-LINE-2 | PIC X(50) | Address line 2; optional. |
| CUST-ADDR-LINE-3 | PIC X(50) | Address line 3; optional. |
| CUST-ADDR-STATE-CD | PIC X(02) | US state code; must be valid (RULE-037). |
| CUST-ADDR-COUNTRY-CD | PIC X(03) | Country code; 3-alpha required (RULE-040). |
| CUST-ADDR-ZIP | PIC X(10) | ZIP code; 5-digit numeric non-zero (RULE-038). Cross-checked against state (RULE-042). |
| CUST-PHONE-NUM-1 | PIC X(15) | Phone (format `(NXX)NXX-XXXX  `); NANP-valid area code required (RULE-041); optional if blank. |
| CUST-PHONE-NUM-2 | PIC X(15) | Secondary phone; same rules as CUST-PHONE-NUM-1. |
| CUST-SSN | PIC 9(09) | Social Security Number stored as 9-digit numeric. Entered as three separate parts; area number exclusions enforced (RULE-033). |
| CUST-GOVT-ISSUED-ID | PIC X(20) | Government-issued ID (e.g., passport). No format validation in code. |
| CUST-DOB-YYYYMMDD | PIC X(10) | Date of birth 'CCYY-MM-DD'; two-stage validation (RULE-046). |
| CUST-EFT-ACCOUNT-ID | PIC X(10) | EFT account ID; 10-digit numeric non-zero (RULE-043). |
| CUST-PRI-CARD-HOLDER-IND | PIC X(01) | Primary cardholder indicator; 'Y' or 'N' (RULE-044). |
| CUST-FICO-CREDIT-SCORE | PIC 9(03) | FICO credit score; range 300–850 (RULE-034). |
| FILLER | PIC X(168) | Reserved. |

**Rules consuming this object:** RULE-033 through RULE-046, RULE-053, RULE-057, RULE-061

---

### CARD-XREF-RECORD
**Copybook:** `legacy/CardDemo/app/cpy/CVACT03Y.cpy`
**Storage:** XREF-FILE (base KSDS keyed by XREF-CARD-NUM); CXACAIX (AIX keyed by account ID); CCXREF (alias used by online programs) — record length 50 bytes

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| XREF-CARD-NUM | PIC X(16) | Card number — primary key. |
| XREF-CUST-ID | PIC 9(09) | Customer ID. |
| XREF-ACCT-ID | PIC 9(11) | Account ID — alternate index (CXACAIX) enables account→card lookup. |
| FILLER | PIC X(14) | Reserved. |

**Usage:** Bidirectional card↔account navigation. RULE-047 (COTRN02C), RULE-063 (CBTRN02C), COBIL00C, COCRDLIC.

---

### TRAN-RECORD (Transaction)
**Copybook:** `legacy/CardDemo/app/cpy/CVTRA05Y.cpy`
**Storage:** TRANSACT — VSAM KSDS, record length 350 bytes
**Primary key:** TRAN-ID PIC X(16) (16-character sequential numeric)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| TRAN-ID | PIC X(16) | Transaction identifier; auto-incremented from last existing ID (RULE-012). |
| TRAN-TYPE-CD | PIC X(02) | Transaction type code. `'01'` = interest (RULE-018); `'02'` = bill payment (RULE-010). |
| TRAN-CAT-CD | PIC 9(04) | Transaction category code. `'05'` = interest; `2` = bill payment. |
| TRAN-SOURCE | PIC X(10) | Source system or channel. `'System'` for interest; `'POS TERM'` for bill payment. |
| TRAN-DESC | PIC X(100) | Description. E.g., `'Int. for a/c 12345678901'` or `'BILL PAYMENT - ONLINE'`. |
| TRAN-AMT | PIC S9(09)V99 | Transaction amount signed. Positive = charge; negative = credit/payment. |
| TRAN-MERCHANT-ID | PIC 9(09) | Merchant identifier. `999999999` for bill payments. |
| TRAN-MERCHANT-NAME | PIC X(50) | Merchant name. `'BILL PAYMENT'` for bill payments. |
| TRAN-MERCHANT-CITY | PIC X(50) | Merchant city. `'N/A'` for bill payments. |
| TRAN-MERCHANT-ZIP | PIC X(10) | Merchant ZIP. `'N/A'` for bill payments. |
| TRAN-CARD-NUM | PIC X(16) | Card number associated with transaction. |
| TRAN-ORIG-TS | PIC X(26) | Original transaction timestamp (format: 'YYYY-MM-DD HH:MM:SS.ssssss'). First 10 chars used for date comparisons. |
| TRAN-PROC-TS | PIC X(26) | Processing timestamp. Used for date-range filtering in reports (RULE-023). |
| FILLER | PIC X(20) | Reserved. |

**Rules consuming this object:** RULE-012, RULE-020, RULE-021, RULE-023, RULE-052, RULE-067, RULE-068, RULE-069

---

### TRAN-CAT-BAL-RECORD (Transaction Category Balance)
**Copybook:** `legacy/CardDemo/app/cpy/CVTRA01Y.cpy`
**Storage:** TCATBAL — VSAM KSDS, record length 50 bytes
**Primary key:** TRAN-CAT-KEY (17 bytes composite)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| TRANCAT-ACCT-ID | PIC 9(11) | Account ID — part 1 of composite key. |
| TRANCAT-TYPE-CD | PIC X(02) | Transaction type code — part 2 of key. |
| TRANCAT-CD | PIC 9(04) | Transaction category code — part 3 of key. |
| TRAN-CAT-BAL | PIC S9(09)V99 | Running balance for this account/type/category combination. |
| FILLER | PIC X(22) | Reserved. |

**Usage:** Input to the interest formula (RULE-007). Accumulated by every batch-posted transaction (RULE-068). One record per unique (account, type, category) combination. Reset indirectly when account's cycle accumulators reset (RULE-009 resets ACCT-CURR-CYC-* but does NOT reset TRAN-CAT-BAL — SME should confirm whether category balances should also reset at cycle end).

---

### DIS-GROUP-RECORD (Disclosure / Interest Rate Group)
**Copybook:** `legacy/CardDemo/app/cpy/CVTRA02Y.cpy`
**Storage:** DISCGRP — VSAM KSDS, record length 50 bytes
**Primary key:** DIS-GROUP-KEY (16 bytes composite)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| DIS-ACCT-GROUP-ID | PIC X(10) | Group identifier (e.g., 'GOLD      ', 'DEFAULT   '). |
| DIS-TRAN-TYPE-CD | PIC X(02) | Transaction type code. |
| DIS-TRAN-CAT-CD | PIC 9(04) | Transaction category code. |
| DIS-INT-RATE | PIC S9(04)V99 | Annual interest rate in percentage × 100 (e.g., 1800 = 18.00%). Divided by 1200 to get monthly decimal rate. |
| FILLER | PIC X(28) | Reserved. |

**Usage:** Read by CBACT04C (interest batch) to determine the applicable rate for each (account group, type, category) combination. If the account's group has no record, 'DEFAULT' group is tried (RULE-016). Zero rate skips interest calculation entirely (RULE-017).

---

### SEC-USER-DATA (User Security Record)
**Copybook:** `legacy/CardDemo/app/cpy/CSUSR01Y.cpy`
**Storage:** USRSEC — VSAM KSDS, record length 80 bytes
**Primary key:** SEC-USR-ID PIC X(08)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| SEC-USR-ID | PIC X(08) | User ID — 8-character; primary key. |
| SEC-USR-FNAME | PIC X(20) | First name. |
| SEC-USR-LNAME | PIC X(20) | Last name. |
| SEC-USR-PWD | PIC X(08) | Password stored as **plain text** (8 chars). Compared to uppercased input (RULE-004). |
| SEC-USR-TYPE | PIC X(01) | User type: 'A' = admin; 'U' = regular user (RULE-005). |
| SEC-USR-FILLER | PIC X(23) | Reserved. |

**Security note:** Passwords are stored plain-text — a critical finding for the modernization security posture. Must be replaced with a hashed credential store.

---

### AUTH-SUMMARY (IMS Segment — CIPAUSMY)
**Copybook:** `legacy/CardDemo/app/app-authorization-ims-db2-mq/cpy/CIPAUSMY.cpy`
**Storage:** IMS database segment (PCB PASFLPCB — summary level)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| PA-APPROVED-AUTH-CNT | PIC S9(04) COMP | Count of approved (resp-code '00') pending authorizations. |
| PA-DECLINED-AUTH-CNT | PIC S9(04) COMP | Count of declined pending authorizations. |
| PA-APPROVED-AUTH-AMT | PIC S9(09)V99 COMP-3 | Total approved authorization amount. |
| PA-DECLINED-AUTH-AMT | PIC S9(09)V99 COMP-3 | Total declined authorization amount. |

**Rules:** RULE-014 (decremented when expired auth is deleted); RULE-015 (deletion threshold — DEFECT confirmed).

---

### AUTH-DETAIL (IMS Segment — CIPAUDTY)
**Copybook:** `legacy/CardDemo/app/app-authorization-ims-db2-mq/cpy/CIPAUDTY.cpy`
**Storage:** IMS database segment (PCB PADFLPCB — detail level, child of AUTH-SUMMARY)

| Field | COBOL Picture | Description |
|-------|---------------|-------------|
| PA-AUTH-DATE-9C | PIC S9(05) COMP-3 | Authorization date stored as `99999 − YYDDD` (complement-encoded Julian day, 2-digit year). See RULE-013. |
| PA-AUTH-RESP-CODE | PIC X(02) | Response code: '00' = approved; any other = declined (RULE-062). |
| PA-TRANSACTION-AMT | PIC S9(10)V99 COMP-3 | Transaction amount requested. |
| PA-APPROVED-AMT | PIC S9(10)V99 COMP-3 | Amount approved (may differ from requested). |

**Rules:** RULE-013 (expiry arithmetic), RULE-014 (count decrement), RULE-015 (deletion defect), RULE-062 (response code display).

---

## VSAM Dataset Reference

| Dataset Name (CICS/JCL) | Entity | Key |
|--------------------------|--------|-----|
| ACCTDAT | Account | ACCT-ID PIC 9(11) |
| CARDDAT | Card | CARD-NUM PIC X(16) |
| CUSTDAT | Customer | CUST-ID PIC 9(09) |
| XREF-FILE / CCXREF | Card-Account XREF | CARD-NUM PIC X(16) |
| CXACAIX | Card-Account XREF (AIX) | ACCT-ID PIC 9(11) |
| TRANSACT | Transaction | TRAN-ID PIC X(16) |
| TCATBAL | Trans Category Balance | (ACCT-ID+TYPE-CD+CAT-CD) 17 B |
| DISCGRP | Disclosure / Rate Group | (GROUP-ID+TYPE-CD+CAT-CD) 16 B |
| USRSEC | User Security | SEC-USR-ID PIC X(08) |
| DALYTRAN | Daily Transactions (input) | Sequential (no key) |
| DALYREJS | Daily Reject Records | Sequential (no key) |
