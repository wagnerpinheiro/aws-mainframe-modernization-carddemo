package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Maps CVACT01Y / ACCTDATA VSAM KSDS.
 * Record length: 300 bytes.
 */
@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    @Column(name = "acct_id")
    private Long id;

    @Column(name = "acct_active_status", length = 1)
    private String activeStatus;

    @Column(name = "acct_curr_bal", precision = 12, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "acct_credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "acct_cash_credit_limit", precision = 12, scale = 2)
    private BigDecimal cashCreditLimit;

    @Column(name = "acct_open_date", length = 10)
    private String openDate;

    @Column(name = "acct_expiration_date", length = 10)
    private String expirationDate;

    @Column(name = "acct_reissue_date", length = 10)
    private String reissueDate;

    @Column(name = "acct_curr_cyc_credit", precision = 12, scale = 2)
    private BigDecimal currCycleCredit;

    @Column(name = "acct_curr_cyc_debit", precision = 12, scale = 2)
    private BigDecimal currCycleDebit;

    @Column(name = "acct_addr_zip", length = 10)
    private String addrZip;

    @Column(name = "acct_group_id", length = 10)
    private String groupId;

    protected AccountEntity() {}

    public AccountEntity(Long id, String activeStatus, BigDecimal currentBalance,
                         BigDecimal creditLimit, BigDecimal cashCreditLimit,
                         String openDate, String expirationDate, String reissueDate,
                         BigDecimal currCycleCredit, BigDecimal currCycleDebit,
                         String addrZip, String groupId) {
        this.id = id;
        this.activeStatus = activeStatus;
        this.currentBalance = currentBalance;
        this.creditLimit = creditLimit;
        this.cashCreditLimit = cashCreditLimit;
        this.openDate = openDate;
        this.expirationDate = expirationDate;
        this.reissueDate = reissueDate;
        this.currCycleCredit = currCycleCredit;
        this.currCycleDebit = currCycleDebit;
        this.addrZip = addrZip;
        this.groupId = groupId;
    }

    public Long getId() { return id; }
    public String getActiveStatus() { return activeStatus; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public BigDecimal getCashCreditLimit() { return cashCreditLimit; }
    public String getOpenDate() { return openDate; }
    public String getExpirationDate() { return expirationDate; }
    public String getReissueDate() { return reissueDate; }
    public BigDecimal getCurrCycleCredit() { return currCycleCredit; }
    public BigDecimal getCurrCycleDebit() { return currCycleDebit; }
    public String getAddrZip() { return addrZip; }
    public String getGroupId() { return groupId; }

    // Mutators used by TransactionPostingService (CBTRN02C 2800-UPDATE-ACCOUNT-REC)
    public void setCurrentBalance(BigDecimal v) { this.currentBalance = v; }
    public void setCurrCycleCredit(BigDecimal v) { this.currCycleCredit = v; }
    public void setCurrCycleDebit(BigDecimal v) { this.currCycleDebit = v; }
}
