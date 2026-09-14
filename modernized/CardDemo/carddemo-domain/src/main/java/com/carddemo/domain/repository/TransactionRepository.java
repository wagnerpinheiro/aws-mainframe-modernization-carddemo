package com.carddemo.domain.repository;

import com.carddemo.domain.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
}
