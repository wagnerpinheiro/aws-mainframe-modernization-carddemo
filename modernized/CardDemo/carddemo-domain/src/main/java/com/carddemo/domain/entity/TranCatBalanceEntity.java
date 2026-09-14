package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Maps CVTRA01Y / TCATBAL VSAM KSDS.
 * Record length: 50 bytes (FILLER 22 bytes not persisted).
 *
 * Accumulates transaction amounts by (account, type, category).
 * Used by CBTRN02C 2700-UPDATE-TCATBAL and CBACT04C interest calculation.
 */
@Entity
@Table(name = "tran_cat_balance")
public class TranCatBalanceEntity {

    @EmbeddedId
    private TranCatBalanceId id;

    @Column(name = "tran_cat_bal", precision = 11, scale = 2)
    private BigDecimal balance;

    protected TranCatBalanceEntity() {}

    public TranCatBalanceEntity(TranCatBalanceId id, BigDecimal initialBalance) {
        this.id = id;
        this.balance = initialBalance;
    }

    public TranCatBalanceId getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
