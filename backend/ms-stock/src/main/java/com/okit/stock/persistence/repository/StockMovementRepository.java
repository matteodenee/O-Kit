package com.okit.stock.persistence.repository;

import com.okit.stock.persistence.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByIdVarianteOrderByIdDesc(Long idVariante, Pageable pageable);
}