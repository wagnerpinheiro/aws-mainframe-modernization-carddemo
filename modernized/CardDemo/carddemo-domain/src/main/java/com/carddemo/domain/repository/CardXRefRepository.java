package com.carddemo.domain.repository;

import com.carddemo.domain.entity.CardXRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXRefRepository extends JpaRepository<CardXRefEntity, String> {
}
