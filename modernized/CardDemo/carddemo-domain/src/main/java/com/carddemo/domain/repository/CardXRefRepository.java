package com.carddemo.domain.repository;

import com.carddemo.domain.entity.CardXRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXRefRepository extends JpaRepository<CardXRefEntity, String> {
    /** Replicates CBACT04C 1110-GET-XREF-DATA: READ XREF-FILE KEY IS FD-XREF-ACCT-ID (alternate key). */
    java.util.Optional<CardXRefEntity> findByAccountId(Long accountId);

    /**
     * Replicates CBSTM03B XREFFILE sequential read (ACCESS MODE IS SEQUENTIAL on XREF-FILE).
     * Returns all entries sorted by card number (primary key order).
     */
    java.util.List<CardXRefEntity> findAllByOrderByCardNumberAsc();
}
