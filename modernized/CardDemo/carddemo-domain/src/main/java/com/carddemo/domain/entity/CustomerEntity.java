package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps CUSTREC.cpy / CUSTFILE VSAM KSDS.
 * Record length: 500 bytes.
 *
 * Security note: CUST-SSN is stored but never logged or included in statement output.
 * SEC-014 fix (DataImportJob): Bean Validation enforces SSN format and FICO range at import time.
 */
@Entity
@Table(name = "customer")
public class CustomerEntity {

    @Id
    @Column(name = "cust_id")
    private Long id;

    @Column(name = "cust_first_name", length = 25)
    private String firstName;

    @Column(name = "cust_middle_name", length = 25)
    private String middleName;

    @Column(name = "cust_last_name", length = 25)
    private String lastName;

    @Column(name = "cust_addr_line_1", length = 50)
    private String addrLine1;

    @Column(name = "cust_addr_line_2", length = 50)
    private String addrLine2;

    @Column(name = "cust_addr_line_3", length = 50)
    private String addrLine3;

    @Column(name = "cust_addr_state_cd", length = 2)
    private String addrStateCd;

    @Column(name = "cust_addr_country_cd", length = 3)
    private String addrCountryCd;

    @Column(name = "cust_addr_zip", length = 10)
    private String addrZip;

    @Column(name = "cust_phone_num_1", length = 15)
    private String phoneNum1;

    @Column(name = "cust_phone_num_2", length = 15)
    private String phoneNum2;

    @Column(name = "cust_ssn")
    private Long ssn;

    @Column(name = "cust_govt_issued_id", length = 20)
    private String govtIssuedId;

    @Column(name = "cust_dob", length = 10)
    private String dob;

    @Column(name = "cust_eft_account_id", length = 10)
    private String eftAccountId;

    @Column(name = "cust_pri_card_holder_ind", length = 1)
    private String priCardHolderInd;

    @Column(name = "cust_fico_credit_score")
    private Integer ficoCreditScore;

    protected CustomerEntity() {}

    public CustomerEntity(Long id, String firstName, String middleName, String lastName,
                          String addrLine1, String addrLine2, String addrLine3,
                          String addrStateCd, String addrCountryCd, String addrZip,
                          String phoneNum1, String phoneNum2, Long ssn, String govtIssuedId,
                          String dob, String eftAccountId, String priCardHolderInd,
                          Integer ficoCreditScore) {
        this.id = id;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.addrLine1 = addrLine1;
        this.addrLine2 = addrLine2;
        this.addrLine3 = addrLine3;
        this.addrStateCd = addrStateCd;
        this.addrCountryCd = addrCountryCd;
        this.addrZip = addrZip;
        this.phoneNum1 = phoneNum1;
        this.phoneNum2 = phoneNum2;
        this.ssn = ssn;
        this.govtIssuedId = govtIssuedId;
        this.dob = dob;
        this.eftAccountId = eftAccountId;
        this.priCardHolderInd = priCardHolderInd;
        this.ficoCreditScore = ficoCreditScore;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    public String getAddrLine1() { return addrLine1; }
    public String getAddrLine2() { return addrLine2; }
    public String getAddrLine3() { return addrLine3; }
    public String getAddrStateCd() { return addrStateCd; }
    public String getAddrCountryCd() { return addrCountryCd; }
    public String getAddrZip() { return addrZip; }
    public String getPhoneNum1() { return phoneNum1; }
    public String getPhoneNum2() { return phoneNum2; }
    public String getGovtIssuedId() { return govtIssuedId; }
    public String getDob() { return dob; }
    public String getEftAccountId() { return eftAccountId; }
    public String getPriCardHolderInd() { return priCardHolderInd; }
    public Integer getFicoCreditScore() { return ficoCreditScore; }

    // Mutators for AccountUpdateService (COACTUPC — customer fields editable from account update screen)
    // NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified
    public void setFirstName(String v) { this.firstName = v; }
    public void setMiddleName(String v) { this.middleName = v; }
    public void setLastName(String v) { this.lastName = v; }
    public void setAddrLine1(String v) { this.addrLine1 = v; }
    public void setAddrLine2(String v) { this.addrLine2 = v; }
    public void setAddrLine3(String v) { this.addrLine3 = v; }
    public void setAddrStateCd(String v) { this.addrStateCd = v; }
    public void setAddrCountryCd(String v) { this.addrCountryCd = v; }
    public void setAddrZip(String v) { this.addrZip = v; }
    public void setPhoneNum1(String v) { this.phoneNum1 = v; }
    public void setPhoneNum2(String v) { this.phoneNum2 = v; }
    public void setFicoCreditScore(Integer v) { this.ficoCreditScore = v; }
    public void setEftAccountId(String v) { this.eftAccountId = v; }
    public void setPriCardHolderInd(String v) { this.priCardHolderInd = v; }
}
