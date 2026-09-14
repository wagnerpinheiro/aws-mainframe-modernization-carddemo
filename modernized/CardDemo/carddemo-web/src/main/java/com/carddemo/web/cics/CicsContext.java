package com.carddemo.web.cics;

/**
 * Immutable value object replacing the COBOL COMMAREA (COCOM01Y) and EIB fields.
 *
 * In the COBOL, CARDDEMO-COMMAREA was a shared mutable struct passed across every
 * CICS XCTL call. Spring Security's SecurityContext replaces the authentication fields
 * (userId, userType). Domain navigation state (currentAccountId, screenId) is modelled
 * here as a clean Java record rather than a flat byte array.
 *
 * This record is designed to be stored in the HTTP session when needed by online screens
 * in Phases 4 and 5 (COACTUPC, COBIL00C, etc.).
 *
 * Note: {@link CicsContext} instances are immutable — create a new one when state changes
 * rather than mutating in place (unlike the COBOL COMMAREA which was mutated in every program).
 */
public record CicsContext(
    /** CDEMO-USER-ID — authenticated user's ID (8 chars max). */
    String userId,
    /** Current account in context — equivalent to CDEMO-ACCT-ID used by Phases 4/5. */
    Long currentAccountId,
    /** Current screen / program name — equivalent to CDEMO-FROM-PROGRAM. */
    String screenId,
    /** HTTP session ID (replaces EIB-level session tracking). */
    String sessionId
) {
    /** Constructs a minimal context for navigation (no account in scope yet). */
    public static CicsContext forUser(String userId, String sessionId) {
        return new CicsContext(userId, null, null, sessionId);
    }

    /** Constructs a context with an account in scope (used by Phase 4/5 screens). */
    public CicsContext withAccount(Long accountId) {
        return new CicsContext(userId, accountId, screenId, sessionId);
    }

    /** Constructs a context navigating to a new screen. */
    public CicsContext withScreen(String newScreenId) {
        return new CicsContext(userId, currentAccountId, newScreenId, sessionId);
    }
}
