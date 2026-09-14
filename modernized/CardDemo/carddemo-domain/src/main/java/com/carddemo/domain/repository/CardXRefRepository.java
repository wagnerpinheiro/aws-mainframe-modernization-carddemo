package com.carddemo.domain.repository;

import com.carddemo.domain.entity.CardXRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXRefRepository extends JpaRepository<CardXRefEntity, String> {
    /** Replicates CBACT04C 1110-GET-XREF-DATA: READ XREF-FILE KEY IS FD-XREF-ACCT-ID (alternate key). */
    java.util.Optional<CardXRefEntity> findByAccountId(Long accountId);
}
