package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Produit;
import com.okit.catalogue.entities.ProduitStatut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProduitRepository extends JpaRepository<Produit, Long>, JpaSpecificationExecutor<Produit> {

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    @Override
    Optional<Produit> findById(Long id);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    Optional<Produit> findWithDetailsByIdProduit(Long idProduit);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    @Override
    Page<Produit> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    @Override
    Page<Produit> findAll(Specification<Produit> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    Page<Produit> findBySaisonIdSaison(Long saisonId, Pageable pageable);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    Page<Produit> findByEquipeIdEquipe(Long equipeId, Pageable pageable);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    Page<Produit> findByMarqueIdMarque(Long marqueId, Pageable pageable);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    Page<Produit> findByStatut(ProduitStatut statut, Pageable pageable);

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    @Query("SELECT p FROM Produit p " +
           "LEFT JOIN p.equipe e " +
           "WHERE (:equipeId IS NULL OR e.idEquipe = :equipeId) " +
           "AND (:saisonId IS NULL OR p.saison.idSaison = :saisonId) " +
           "AND (:marqueId IS NULL OR p.marque.idMarque = :marqueId) " +
           "AND (:statut IS NULL OR p.statut = :statut)")
    Page<Produit> findByFilters(
            @Param("equipeId") Long equipeId,
            @Param("saisonId") Long saisonId,
            @Param("marqueId") Long marqueId,
            @Param("statut") ProduitStatut statut,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"saison", "marque", "tauxTva", "equipe"})
    @Query("SELECT p FROM Produit p " +
           "LEFT JOIN p.equipe e " +
           "WHERE (:equipeId IS NULL OR e.idEquipe = :equipeId) " +
           "AND (:saisonId IS NULL OR p.saison.idSaison = :saisonId) " +
           "AND (:marqueId IS NULL OR p.marque.idMarque = :marqueId) " +
           "AND (:statut IS NULL OR p.statut = :statut) " +
           "AND (:enStock IS NULL OR p.enStock = :enStock)")
    Page<Produit> findByFilters(
            @Param("equipeId") Long equipeId,
            @Param("saisonId") Long saisonId,
            @Param("marqueId") Long marqueId,
            @Param("statut") ProduitStatut statut,
            @Param("enStock") Boolean enStock,
            Pageable pageable
    );
}
