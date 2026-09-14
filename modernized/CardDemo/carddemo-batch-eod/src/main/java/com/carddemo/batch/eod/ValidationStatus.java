package com.carddemo.batch.eod;

/** Outcome of validating one DALYTRAN record against XREF and account data. */
public enum ValidationStatus {
    /** Card found in XREF, account found, within limit, not expired — passes validation. */
    VALID,
    /** DALYTRAN-CARD-NUM not found in XREF-FILE — COBOL: reject 100 "INVALID CARD NUMBER FOUND". */
    CARD_NOT_FOUND,
    /** Card found in XREF, but XREF-ACCT-ID not found in ACCOUNT-FILE — COBOL: reject 101. */
    ACCOUNT_NOT_FOUND,
    /** Cycle-to-date balance would exceed credit limit — COBOL: reject 102 "OVERLIMIT TRANSACTION". */
    OVERLIMIT,
    /** Account expiry date is before transaction origin date — COBOL: reject 103. */
    EXPIRED
}
