package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Maps CVTRA05Y / TRANSACT VSAM KSDS.
 * Record length: 350 bytes (same layout as DALYTRAN-RECORD / CVTRA06Y).
 *
 * Written by CBTRN02C 2900-WRITE-TRANSACTION-FILE for each accepted transaction.
 * processTimestamp (TRAN-PROC-TS) is set at posting time; the original
 * timestamp (TRAN-ORIG-TS) comes from the DALYTRAN input record.
 */
@Entity
@Table(name = "transaction")
public class TransactionEntity {

    @Id
    @Column(name = "tran_id", length = 16)
    private String id;

    @Column(name = "tran_type_cd", length = 2)
    private String typeCode;

    @Column(name = "tran_cat_cd")
    private Integer categoryCode;

    @Column(name = "tran_source", length = 10)
    private String source;

    @Column(name = "tran_desc", length = 100)
    private String description;

    @Column(name = "tran_amt", precision = 11, scale = 2)
    private BigDecimal amount;

    @Column(name = "tran_merchant_id")
    private Long merchantId;

    @Column(name = "tran_merchant_name", length = 50)
    private String merchantName;

    @Column(name = "tran_merchant_city", length = 50)
    private String merchantCity;

    @Column(name = "tran_merchant_zip", length = 10)
    private String merchantZip;

    @Column(name = "tran_card_num", length = 16)
    private String cardNumber;

    @Column(name = "tran_orig_ts", length = 26)
    private String originalTimestamp;

    @Column(name = "tran_proc_ts", length = 26)
    private String processTimestamp;

    protected TransactionEntity() {}

    public TransactionEntity(String id, String typeCode, Integer categoryCode,
                             String source, String description, BigDecimal amount,
                             Long merchantId, String merchantName, String merchantCity,
                             String merchantZip, String cardNumber,
                             String originalTimestamp, String processTimestamp) {
        this.id = id;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
        this.source = source;
        this.description = description;
        this.amount = amount;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.merchantCity = merchantCity;
        this.merchantZip = merchantZip;
        this.cardNumber = cardNumber;
        this.originalTimestamp = originalTimestamp;
        this.processTimestamp = processTimestamp;
    }

    public String getId() { return id; }
    public String getTypeCode() { return typeCode; }
    public Integer getCategoryCode() { return categoryCode; }
    public String getSource() { return source; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public Long getMerchantId() { return merchantId; }
    public String getMerchantName() { return merchantName; }
    public String getMerchantCity() { return merchantCity; }
    public String getMerchantZip() { return merchantZip; }
    public String getCardNumber() { return cardNumber; }
    public String getOriginalTimestamp() { return originalTimestamp; }
    public String getProcessTimestamp() { return processTimestamp; }
}
