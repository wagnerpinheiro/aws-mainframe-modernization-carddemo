package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps CVACT03Y / CARDXREF VSAM KSDS.
 * Record length: 50 bytes (FILLER 14 bytes not persisted).
 */
@Entity
@Table(name = "card_xref")
public class CardXRefEntity {

    @Id
    @Column(name = "card_number", length = 16)
    private String cardNumber;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "account_id")
    private Long accountId;

    protected CardXRefEntity() {}

    public CardXRefEntity(String cardNumber, Long customerId, Long accountId) {
        this.cardNumber = cardNumber;
        this.customerId = customerId;
        this.accountId = accountId;
    }

    public String getCardNumber() { return cardNumber; }
    public Long getCustomerId() { return customerId; }
    public Long getAccountId() { return accountId; }
}
