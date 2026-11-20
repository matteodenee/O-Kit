package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Saison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaisonRepository extends JpaRepository<Saison, Long> {
    Optional<Saison> findByNom(String nom);
}
