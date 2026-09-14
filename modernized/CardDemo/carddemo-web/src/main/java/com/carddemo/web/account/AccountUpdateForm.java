package com.carddemo.web.account;

import java.math.BigDecimal;

/**
 * Form DTO for COACTUPC account+customer update screen.
 * Includes version field for RULE-053 optimistic locking.
 */
public record AccountUpdateForm(
    Long accountId,
    Long version,            // RULE-053: optimistic lock — submitted version must match DB
    String activeStatus,     // RULE-032
    BigDecimal creditLimit,  // RULE-045
    BigDecimal cashCreditLimit, // RULE-045
    // Customer fields
    String firstName,        // RULE-035
    String middleName,       // RULE-035 (optional)
    String lastName,         // RULE-035
    String addrLine1,        // RULE-036
    String addrLine2,
    String addrLine3,
    String addrStateCd,      // RULE-037
    String addrZip,          // RULE-038
    String addrCity,         // RULE-039 (mapped to addrLine3 for display)
    String addrCountryCd,    // RULE-040
    String phoneNum1,        // RULE-041 (optional)
    String phoneNum2,        // RULE-041 (optional)
    Integer ficoCreditScore, // RULE-034
    String eftAccountId,     // RULE-043
    String priCardHolderInd  // RULE-044
) {}
