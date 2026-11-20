package com.okit.stock.persistence.repository;

import com.okit.stock.persistence.entity.IdempotentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, String> {
}