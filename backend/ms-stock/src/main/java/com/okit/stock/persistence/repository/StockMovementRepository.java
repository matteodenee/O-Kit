package com.okit.stock.persistence.repository;

import com.okit.stock.persistence.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}