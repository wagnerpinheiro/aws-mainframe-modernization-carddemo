package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Maps CVTRA02Y / DISCGRP VSAM KSDS.
 * Record length: 50 bytes (FILLER 28 bytes not persisted).
 *
 * Stores annual interest rate (percentage) per (accountGroupId, typeCode, categoryCode).
 * CBACT04C 1200-GET-INTEREST-RATE reads by this composite key; if not found (status '23'),
 * falls back to groupId="DEFAULT".
 */
@Entity
@Table(name = "discount_group")
public class DiscountGroupEntity {

    @EmbeddedId
    private DiscountGroupId id;

    /** Annual interest rate as a percentage (e.g., 15.00 = 15% APR). PIC S9(4)V99. */
    @Column(name = "dis_int_rate", precision = 6, scale = 2)
    private BigDecimal interestRate;

    protected DiscountGroupEntity() {}

    public DiscountGroupEntity(DiscountGroupId id, BigDecimal interestRate) {
        this.id = id;
        this.interestRate = interestRate;
    }

    public DiscountGroupId getId() { return id; }
    public BigDecimal getInterestRate() { return interestRate; }
}
