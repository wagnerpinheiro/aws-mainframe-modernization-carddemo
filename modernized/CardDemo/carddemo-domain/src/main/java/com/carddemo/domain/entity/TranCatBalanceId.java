package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for TRAN-CAT-BAL-RECORD (CVTRA01Y).
 * Key: TRANCAT-ACCT-ID (9(11)) + TRANCAT-TYPE-CD (X(2)) + TRANCAT-CD (9(4)).
 */
@Embeddable
public class TranCatBalanceId implements Serializable {

    @Column(name = "trancat_acct_id")
    private Long accountId;

    @Column(name = "trancat_type_cd", length = 2)
    private String typeCode;

    @Column(name = "trancat_cd")
    private Integer categoryCode;

    protected TranCatBalanceId() {}

    public TranCatBalanceId(Long accountId, String typeCode, Integer categoryCode) {
        this.accountId = accountId;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
    }

    public Long getAccountId() { return accountId; }
    public String getTypeCode() { return typeCode; }
    public Integer getCategoryCode() { return categoryCode; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TranCatBalanceId that)) return false;
        return Objects.equals(accountId, that.accountId)
            && Objects.equals(typeCode, that.typeCode)
            && Objects.equals(categoryCode, that.categoryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, typeCode, categoryCode);
    }
}
