# CardDemo Phase 1 — BASELINE

**Date:** 2026-09-13  
**Source:** Static analysis of `legacy/CardDemo/app/cbl/CBTRN01C.cbl` against
`legacy/CardDemo/app/data/ASCII/` fixtures.  
**Equivalence strategy:** Trace-based (golden-master) — no live z/OS runtime available.
GnuCOBOL 3.2.0 syntax check passes: `cobc -fsyntax-only -I app/cpy/ app/cbl/CBTRN01C.cbl`.

---

## CBTRN01C — Transaction File Validator

### What the COBOL program does (from source, lines 154–496)

1. Opens 6 files: DALYTRAN (INPUT sequential), CUSTOMER, XREF, CARD, ACCOUNT, TRANSACT (all INPUT indexed/VSAM).  
   — CUSTOMER-FILE, CARD-FILE, TRANSACT-FILE are opened but never read (dead opens).  
2. Reads DALYTRAN sequentially until EOF (`DALYTRAN-STATUS = '10'`).  
3. For each record:  
   a. Looks up `DALYTRAN-CARD-NUM` in `XREF-FILE` (random, key = `FD-XREF-CARD-NUM`).  
      - Found → `WS-XREF-READ-STATUS = 0`; moves `XREF-ACCT-ID` to `ACCT-ID`.  
      - Not found → `WS-XREF-READ-STATUS = 4`; DISPLAYs "CARD NUMBER … COULD NOT BE VERIFIED. SKIPPING".  
   b. If card found: reads `ACCOUNT-FILE` (key = `FD-ACCT-ID`).  
      - Found → DISPLAYs "SUCCESSFUL READ OF ACCOUNT FILE".  
      - Not found → `WS-ACCT-READ-STATUS = 4`; DISPLAYs "ACCOUNT … NOT FOUND".  
4. Closes all files.  
5. **No file writes** — output is DISPLAY-only (console). The Q6 enhancement (structured CSV reject file) is added in the Java equivalent per the established premise.

### Expected outputs against the full fixture set

| Metric | Value | Derivation |
|---|---|---|
| Total DALYTRAN records | 300 | `wc -l dailytran.txt` |
| Distinct card numbers in DALYTRAN | 50 | `cut -c263-278 | sort -u | wc -l` |
| Card numbers in DALYTRAN NOT in XREF | **0** | `comm -23 tran_cards xref_cards` |
| Distinct XREF account IDs | 50 | `cut -c26-36 cardxref.txt | sort -u | wc -l` |
| XREF account IDs NOT in acctdata | **0** | `comm -23 xref_accts acct_ids` |

**Golden-master conclusion for full fixture run:**
- All 300 records → card found in XREF → `ValidationStatus.VALID`
- All 300 records → account found in ACCOUNT_FILE → `ValidationStatus.VALID`
- **Expected valid count: 300**
- **Expected CARD_NOT_FOUND count: 0**
- **Expected ACCOUNT_NOT_FOUND count: 0**
- **Expected reject CSV rows: 0**

### COBOL vs Java equivalence map

| COBOL behavior | Java equivalent |
|---|---|
| `WS-XREF-READ-STATUS = 4` (card not in XREF) | `ValidationStatus.CARD_NOT_FOUND` |
| `WS-ACCT-READ-STATUS = 4` (account not found) | `ValidationStatus.ACCOUNT_NOT_FOUND` |
| `WS-XREF-READ-STATUS = 0 AND WS-ACCT-READ-STATUS = 0` | `ValidationStatus.VALID` |
| DISPLAY "COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-…" | `log.warn("Card not found in XREF: cardNumber={}, transactionId={}")` |
| DISPLAY "ACCOUNT … NOT FOUND" | `log.warn("Account not found: accountId={}, transactionId={}")` |
| DISPLAY "SUCCESSFUL READ OF XREF / ACCOUNT FILE" | `log.debug(…)` |
| No reject file write | Q6 enhancement: CSV reject file written to `carddemo.batch.reject-output` |

---

## Field layout verification

### DALYTRAN-RECORD (CVTRA06Y, 350 bytes)

Verified against `dailytran.txt` (wc -c 105300 / 300 lines = 350 bytes/record + newline):

| Field | Bytes | Example (record 1) |
|---|---|---|
| DALYTRAN-ID | 1–16 | `0000000000683580` |
| DALYTRAN-TYPE-CD | 17–18 | `01` |
| DALYTRAN-CAT-CD | 19–22 | `0001` |
| DALYTRAN-SOURCE | 23–32 | `POS TERM  ` |
| DALYTRAN-DESC | 33–132 | `Purchase at Abshire-Lowe…` |
| DALYTRAN-AMT | 133–143 | `0000005047G` (S9(9)V99, G=+7 → $504.77) |
| DALYTRAN-MERCHANT-ID | 144–152 | `800000000` |
| DALYTRAN-MERCHANT-NAME | 153–202 | `Abshire-Lowe…` |
| DALYTRAN-MERCHANT-CITY | 203–252 | `North Enoshaven…` |
| DALYTRAN-MERCHANT-ZIP | 253–262 | `72112     ` |
| DALYTRAN-CARD-NUM | 263–278 | `4859452612877065` |
| DALYTRAN-ORIG-TS | 279–304 | `2022-06-10 19:27:53.000000` |
| DALYTRAN-PROC-TS | 305–330 | `                          ` |
| FILLER | 331–350 | (not captured) |

### CARD-XREF-RECORD (CVACT03Y, file: 36 bytes/record, filler not stored)

`cardxref.txt`: 1850 bytes / 50 records = 37 bytes/record (36 data + newline). FILLER(14) absent from ASCII file.

| Field | Bytes | 
|---|---|
| XREF-CARD-NUM | 1–16 |
| XREF-CUST-ID | 17–25 |
| XREF-ACCT-ID | 26–36 |

### ACCOUNT-RECORD (CVACT01Y, 300 bytes)

`acctdata.txt`: 15050 bytes / 50 records = 301 bytes/record (300 data + newline). All fields present.

| Field | Bytes | Example (account 1) |
|---|---|---|
| ACCT-ID | 1–11 | `00000000001` |
| ACCT-ACTIVE-STATUS | 12 | `Y` |
| ACCT-CURR-BAL | 13–24 | `00000001940{` → $1940.00 |
| ACCT-CREDIT-LIMIT | 25–36 | `00000020200{` → $2020.00 |
| ACCT-CASH-CREDIT-LIMIT | 37–48 | `00000010200{` → $1020.00 |
| ACCT-OPEN-DATE | 49–58 | `2014-11-20` |
| ACCT-EXPIRAION-DATE | 59–68 | `2025-05-20` |
| ACCT-REISSUE-DATE | 69–78 | `2025-05-20` |
| ACCT-CURR-CYC-CREDIT | 79–90 | `00000000000{` → $0.00 |
| ACCT-CURR-CYC-DEBIT | 91–102 | `00000000000{` → $0.00 |
| ACCT-ADDR-ZIP | 103–112 | `A000000000` |
| ACCT-GROUP-ID | 113–122 | varies |
| FILLER | 123–300 | spaces |

---

## Phase 1 exit criteria checklist (CBTRN01C pilot)

- [ ] `TransactionValidationJob` compiles with `mvn compile`
- [ ] Full fixture run: 300 valid, 0 rejects (`TransactionValidationJobTest#fullFixtureRun`)
- [ ] Card-not-found scenario: synthetic record with unknown card → 1 reject CSV row (`#cardNotFoundGoesToReject`)
- [ ] Account-not-found scenario: XREF entry pointing to non-existent account → 1 reject CSV row (`#accountNotFoundGoesToReject`)
- [ ] Empty DALYTRAN file → job completes with EXIT_STATUS=COMPLETED, 0 reads (`#emptyFileCompletesCleanly`)
- [ ] `mvn test` passes green in `carddemo-batch-eod`
