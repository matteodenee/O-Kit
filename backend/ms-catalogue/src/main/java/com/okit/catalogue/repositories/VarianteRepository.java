package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Variante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface VarianteRepository extends JpaRepository<Variante, Long> {

    @Query("SELECT v.produit.idProduit AS produitId, v.idVariante AS varianteId " +
           "FROM Variante v " +
           "WHERE v.produit.idProduit IN :produitIds " +
           "ORDER BY v.idVariante")
    List<VarianteIdView> findVarianteIdsByProduitIds(@Param("produitIds") Collection<Long> produitIds);

    interface VarianteIdView {
        Long getProduitId();

        Long getVarianteId();
    }
}
