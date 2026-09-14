package com.carddemo.domain.repository;

import com.carddemo.domain.entity.TranCatBalanceEntity;
import com.carddemo.domain.entity.TranCatBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranCatBalanceRepository extends JpaRepository<TranCatBalanceEntity, TranCatBalanceId> {
}
