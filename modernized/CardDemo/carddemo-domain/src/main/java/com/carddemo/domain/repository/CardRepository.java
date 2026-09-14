package com.carddemo.domain.repository;

import com.carddemo.domain.entity.CardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<CardEntity, String> {
    java.util.List<CardEntity> findAllByOrderByCardNumberAsc();
}
