package com.carddemo.batch.eod;

/** Outcome of validating one DALYTRAN record against XREF and account data. */
public enum ValidationStatus {
    /** Card found in XREF, account found in ACCOUNT_FILE — passes validation. */
    VALID,
    /** DALYTRAN-CARD-NUM not found in XREF-FILE — COBOL: "COULD NOT BE VERIFIED". */
    CARD_NOT_FOUND,
    /** Card found in XREF, but XREF-ACCT-ID not found in ACCOUNT-FILE — COBOL: "ACCOUNT NOT FOUND". */
    ACCOUNT_NOT_FOUND
}
