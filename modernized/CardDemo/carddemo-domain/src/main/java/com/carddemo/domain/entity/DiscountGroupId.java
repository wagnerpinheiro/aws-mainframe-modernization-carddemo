package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for DIS-GROUP-RECORD (CVTRA02Y).
 * Key: DIS-ACCT-GROUP-ID (X(10)) + DIS-TRAN-TYPE-CD (X(2)) + DIS-TRAN-CAT-CD (9(4)).
 */
@Embeddable
public class DiscountGroupId implements Serializable {

    @Column(name = "dis_acct_group_id", length = 10)
    private String accountGroupId;

    @Column(name = "dis_tran_type_cd", length = 2)
    private String typeCode;

    @Column(name = "dis_tran_cat_cd")
    private Integer categoryCode;

    protected DiscountGroupId() {}

    public DiscountGroupId(String accountGroupId, String typeCode, Integer categoryCode) {
        this.accountGroupId = accountGroupId;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
    }

    public String getAccountGroupId() { return accountGroupId; }
    public String getTypeCode() { return typeCode; }
    public Integer getCategoryCode() { return categoryCode; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscountGroupId that)) return false;
        return Objects.equals(accountGroupId, that.accountGroupId)
            && Objects.equals(typeCode, that.typeCode)
            && Objects.equals(categoryCode, that.categoryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountGroupId, typeCode, categoryCode);
    }
}
