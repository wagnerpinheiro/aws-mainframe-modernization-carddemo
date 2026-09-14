package com.carddemo.domain.repository;

import com.carddemo.domain.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    /**
     * Replicates COSTM01 TRNX-FILE key order: (TRNX-CARD-NUM + TRNX-ID) — indexed sequential.
     * Used by StatementGenerationJob to group transactions by card number.
     */
    java.util.List<TransactionEntity> findAllByCardNumberOrderByIdAsc(String cardNumber);
}
