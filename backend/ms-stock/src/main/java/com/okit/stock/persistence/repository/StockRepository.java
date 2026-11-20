package com.okit.stock.persistence.repository;

import com.okit.stock.persistence.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByIdVariante(Long idVariante);

    List<Stock> findByIdVarianteIn(Collection<Long> ids);

    /** SELECT ... FOR UPDATE, trié par id_variante pour éviter les deadlocks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.idVariante in :ids order by s.idVariante asc")
    List<Stock> lockAllByIdVarianteIn(Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.idVariante = :id")
    Optional<Stock> lockByIdVariante(Long id);
}