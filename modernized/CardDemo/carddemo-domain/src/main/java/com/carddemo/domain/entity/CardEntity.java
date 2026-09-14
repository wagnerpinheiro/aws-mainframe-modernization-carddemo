package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps CVACT02Y / CARDDATA VSAM KSDS. Record length: 150 bytes.
 *
 * SEC-005 (PCI DSS fix): CARD-CVV-CD field is intentionally absent from this entity
 * and from all API responses. It is not stored, logged, or exported.
 */
@Entity
@Table(name = "card")
public class CardEntity {

    @Id
    @Column(name = "card_num", length = 16)
    private String cardNumber;

    @Column(name = "card_acct_id")
    private Long accountId;

    @Column(name = "card_embossed_name", length = 50)
    private String embossedName;

    @Column(name = "card_expiration_date", length = 10)
    private String expirationDate;

    @Column(name = "card_active_status", length = 1)
    private String activeStatus;

    protected CardEntity() {}

    public CardEntity(String cardNumber, Long accountId, String embossedName,
                      String expirationDate, String activeStatus) {
        this.cardNumber = cardNumber;
        this.accountId = accountId;
        this.embossedName = embossedName;
        this.expirationDate = expirationDate;
        this.activeStatus = activeStatus;
    }

    public String getCardNumber() { return cardNumber; }
    public Long getAccountId() { return accountId; }
    public String getEmbossedName() { return embossedName; }
    public String getExpirationDate() { return expirationDate; }
    public String getActiveStatus() { return activeStatus; }

    // Mutators for CardUpdateService (COCRDUPC)
    // NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified
    public void setEmbossedName(String v) { this.embossedName = v; }
    public void setExpirationDate(String v) { this.expirationDate = v; }
    public void setActiveStatus(String v) { this.activeStatus = v; }
}
