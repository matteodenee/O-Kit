package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.TauxTva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface TauxTvaRepository extends JpaRepository<TauxTva, Long> {
    Optional<TauxTva> findByTaux(BigDecimal taux);
}
