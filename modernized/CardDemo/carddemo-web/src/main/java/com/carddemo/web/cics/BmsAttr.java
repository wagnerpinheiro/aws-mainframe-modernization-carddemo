package com.carddemo.web.cics;

/**
 * Replaces DFHBMSCA copybook — BMS field attribute values.
 * Used to model field display properties from COBOL BMS maps.
 * In the web layer, these map to HTML input attributes (readonly, type=password, etc.).
 */
public enum BmsAttr {
    /** Input field — user can type. Equivalent to DFHBMFSE (unprotected). */
    UNPROTECTED,
    /** Display-only field — user cannot edit. Equivalent to DFHBMPRO (protected). */
    PROTECTED,
    /** Skip field — cursor moves past. Equivalent to DFHBMASN (autoskip). */
    ASKIP,
    /** Hidden/dark field — e.g., password. Equivalent to DFHBMNOD (dark). */
    DARK,
    /** High-intensity display. Equivalent to DFHBMBRY (bright). */
    BRIGHT,
    /** Normal intensity. */
    NORMAL
}
