package com.okit.catalogue.repositories;

import com.okit.catalogue.entities.Equipe;
import com.okit.catalogue.entities.Produit;
import com.okit.catalogue.entities.ProduitStatut;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

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

    public static Specification<Produit> withEquipeNom(String equipeNom) {
        return (root, query, cb) -> {
            if (equipeNom == null || equipeNom.isBlank()) {
                return cb.conjunction();
            }
            Join<Produit, Equipe> equipeJoin = root.join("equipe", JoinType.LEFT);
            return cb.equal(cb.lower(equipeJoin.get("nom")), equipeNom.trim().toLowerCase(Locale.ROOT));
        };
    }

    public static Specification<Produit> withSaisonNom(String saisonNom) {
        return (root, query, cb) -> {
            if (saisonNom == null || saisonNom.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("saison").get("nom")), saisonNom.trim().toLowerCase(Locale.ROOT));
        };
    }

    public static Specification<Produit> withMarqueNom(String marqueNom) {
        return (root, query, cb) -> {
            if (marqueNom == null || marqueNom.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("marque").get("nomMarque")), marqueNom.trim().toLowerCase(Locale.ROOT));
        };
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

    public static Specification<Produit> withFilters(
            Long equipeId,
            Long saisonId,
            Long marqueId,
            String equipeNom,
            String saisonNom,
            String marqueNom,
            ProduitStatut statut
    ) {
        Specification<Produit> spec = withFilters(equipeId, saisonId, marqueId, statut);
        if (equipeNom != null && !equipeNom.isBlank()) {
            spec = spec.and(withEquipeNom(equipeNom));
        }
        if (saisonNom != null && !saisonNom.isBlank()) {
            spec = spec.and(withSaisonNom(saisonNom));
        }
        if (marqueNom != null && !marqueNom.isBlank()) {
            spec = spec.and(withMarqueNom(marqueNom));
        }
        return spec;
    }
}
