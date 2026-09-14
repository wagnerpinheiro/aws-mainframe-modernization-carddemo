package com.carddemo.web.cics;

/**
 * Replaces DFHAID copybook — CICS attention identifier constants.
 * In the COBOL, these were 88-level condition codes (e.g., WHEN DFHENTER, WHEN DFHPF3).
 * In Java, they are named enum constants. The Web layer translates HTTP form submissions
 * to the closest equivalent: form submit = ENTER, browser back = PF3.
 */
public enum CicsAid {
    ENTER,
    PF1, PF2, PF3, PF4, PF5, PF6, PF7, PF8, PF9, PF10, PF11, PF12,
    PF13, PF14, PF15, PF16, PF17, PF18, PF19, PF20, PF21, PF22, PF23, PF24,
    PA1, PA2,
    CLEAR,
    OTHER
}
