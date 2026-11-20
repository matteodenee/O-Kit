package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Marque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarqueRepository extends JpaRepository<Marque, Long> {
    Optional<Marque> findByNomMarque(String nomMarque);
}
