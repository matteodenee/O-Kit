package com.okit.commande.client;

import com.okit.commande.config.CommandeProperties;
import com.okit.commande.domain.exception.NotFoundException;
import com.okit.commande.domain.exception.ServiceIndisponibleException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Appel synchrone (REST) à ms-catalogue pour lire le prix, la TVA,
 * le flag "estFlocable" et les variantes (tailles) d'un produit.
 */
@Component
public class CatalogueClient {

    private final RestClient restClient;

    public CatalogueClient(RestClient.Builder builder, CommandeProperties props) {
        this.restClient = builder.baseUrl(props.services().catalogueUrl()).build();
    }

    public ProduitCatalogue getProduit(Long idProduit) {
        try {
            ProduitCatalogue produit = restClient.get()
                    .uri("/api/v1/products/{id}", idProduit)
                    .retrieve()
                    .body(ProduitCatalogue.class);
            if (produit == null) {
                throw new NotFoundException("Produit " + idProduit + " introuvable");
            }
            return produit;
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Produit " + idProduit + " introuvable");
        } catch (RestClientException e) {
            throw new ServiceIndisponibleException("catalogue", e);
        }
    }

    /** Uniquement les champs utiles de ProduitDetailDto (les autres sont ignorés par Jackson). */
    public record ProduitCatalogue(
            Long id,
            String titre,
            BigDecimal prixDeBaseHT,
            BigDecimal prixApresPromotionTTC,
            BigDecimal tauxTva,
            Boolean estFlocable,
            String statut,
            List<Variante> variantes
    ) {
        public record Variante(Long id, String taille) {
        }

        public boolean estActif() {
            return "ACTIF".equals(statut);
        }

        public boolean flocable() {
            return Boolean.TRUE.equals(estFlocable);
        }

        public Optional<Variante> variante(Long idVariante) {
            return variantes == null ? Optional.empty()
                    : variantes.stream().filter(v -> v.id().equals(idVariante)).findFirst();
        }

        /** Prix HT appliqué : si une promotion est active, on repasse du TTC remisé au HT. */
        public BigDecimal prixUnitaireHt() {
            if (prixApresPromotionTTC == null) {
                return prixDeBaseHT.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal diviseur = BigDecimal.ONE.add(tauxTva.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            return prixApresPromotionTTC.divide(diviseur, 2, RoundingMode.HALF_UP);
        }
    }
}
