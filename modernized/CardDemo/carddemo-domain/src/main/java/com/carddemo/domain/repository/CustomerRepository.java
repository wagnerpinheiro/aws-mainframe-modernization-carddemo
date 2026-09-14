package com.carddemo.domain.repository;

import com.carddemo.domain.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
}
