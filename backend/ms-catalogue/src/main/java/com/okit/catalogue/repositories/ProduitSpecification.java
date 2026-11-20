package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Produit;
import com.okit.catalogue.entities.ProduitStatut;
import org.springframework.data.jpa.domain.Specification;

public final class ProduitSpecification {

    private ProduitSpecification() {
    }

    public static Specification<Produit> withEquipeId(Long equipeId) {
        return (root, query, cb) ->
                equipeId == null ? cb.conjunction() : cb.equal(root.get("equipe").get("idEquipe"), equipeId);
    }

    public static Specification<Produit> withSaisonId(Long saisonId) {
        return (root, query, cb) ->
                saisonId == null ? cb.conjunction() : cb.equal(root.get("saison").get("idSaison"), saisonId);
    }

    public static Specification<Produit> withMarqueId(Long marqueId) {
        return (root, query, cb) ->
                marqueId == null ? cb.conjunction() : cb.equal(root.get("marque").get("idMarque"), marqueId);
    }

    public static Specification<Produit> withStatut(ProduitStatut statut) {
        return (root, query, cb) ->
                statut == null ? cb.conjunction() : cb.equal(root.get("statut"), statut);
    }

    public static Specification<Produit> withEnStock(Boolean enStock) {
        return (root, query, cb) ->
                enStock == null ? cb.conjunction() : cb.equal(root.get("enStock"), enStock);
    }

    public static Specification<Produit> withFilters(Long equipeId, Long saisonId, Long marqueId, ProduitStatut statut) {
        Specification<Produit> spec = Specification.where(null);
        if (equipeId != null) {
            spec = spec.and(withEquipeId(equipeId));
        }
        if (saisonId != null) {
            spec = spec.and(withSaisonId(saisonId));
        }
        if (marqueId != null) {
            spec = spec.and(withMarqueId(marqueId));
        }
        if (statut != null) {
            spec = spec.and(withStatut(statut));
        }
        return spec;
    }

    public static Specification<Produit> withFilters(Long equipeId, Long saisonId, Long marqueId, ProduitStatut statut, Boolean enStock) {
        Specification<Produit> spec = withFilters(equipeId, saisonId, marqueId, statut);
        if (enStock != null) {
            spec = spec.and(withEnStock(enStock));
        }
        return spec;
    }
}
