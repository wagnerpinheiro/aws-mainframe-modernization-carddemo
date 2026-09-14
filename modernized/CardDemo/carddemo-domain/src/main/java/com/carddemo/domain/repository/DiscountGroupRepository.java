package com.carddemo.domain.repository;

import com.carddemo.domain.entity.DiscountGroupEntity;
import com.carddemo.domain.entity.DiscountGroupId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountGroupRepository extends JpaRepository<DiscountGroupEntity, DiscountGroupId> {
}
