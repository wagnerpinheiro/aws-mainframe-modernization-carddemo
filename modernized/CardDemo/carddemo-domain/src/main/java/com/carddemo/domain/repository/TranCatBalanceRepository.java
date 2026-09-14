package com.carddemo.domain.repository;

import com.carddemo.domain.entity.TranCatBalanceEntity;
import com.carddemo.domain.entity.TranCatBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranCatBalanceRepository extends JpaRepository<TranCatBalanceEntity, TranCatBalanceId> {
    /**
     * Returns all records sorted by the composite key — replicates CBACT04C's sequential
     * read of TCATBAL-FILE (ORGANIZATION IS INDEXED, ACCESS MODE IS SEQUENTIAL), which
     * returns records in ascending key order (accountId, typeCode, categoryCode).
     */
    java.util.List<TranCatBalanceEntity> findAllByOrderByIdAccountIdAscIdTypeCodeAscIdCategoryCodeAsc();
}
