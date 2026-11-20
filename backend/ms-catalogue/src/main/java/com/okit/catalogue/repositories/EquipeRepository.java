package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Equipe;
import com.okit.catalogue.entities.EquipeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipeRepository extends JpaRepository<Equipe, Long> {
    Optional<Equipe> findByNom(String nom);
    List<Equipe> findByType(EquipeType type);
}
