package com.okit.chatbot.client;

import com.okit.chatbot.config.ChatbotProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Appels REST vers les autres microservices O'Kit.
 * <p>
 * Le stock et les commandes sont appelés AVEC LE JWT DE L'ACHETEUR : le chatbot ne voit
 * donc que ce que l'acheteur a le droit de voir (ses propres commandes uniquement).
 * Le catalogue est en lecture publique.
 */
@Component
public class OkitApiClient {

    private final RestClient catalogue;
    private final RestClient stock;
    private final RestClient commande;

    public OkitApiClient(RestClient.Builder builder, ChatbotProperties props) {
        // Timeouts courts propres à ces appels (le client LLM de Spring AI garde les siens, plus longs).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient.Builder base = builder.clone().requestFactory(factory);
        this.catalogue = base.clone().baseUrl(props.services().catalogueUrl()).build();
        this.stock = base.clone().baseUrl(props.services().stockUrl()).build();
        this.commande = base.clone().baseUrl(props.services().commandeUrl()).build();
    }

    /** Produits actifs du catalogue (une page suffit pour le MVP). */
    public List<ProduitResume> produitsActifs() {
        PageProduits page = catalogue.get()
                .uri("/api/v1/products?statut=ACTIF&size=100")
                .retrieve()
                .body(PageProduits.class);
        return page == null || page.content() == null ? List.of() : page.content();
    }

    public ProduitDetail produit(long idProduit) {
        return catalogue.get()
                .uri("/api/v1/products/{id}", idProduit)
                .retrieve()
                .body(ProduitDetail.class);
    }

    public List<StatutStock> stocks(List<Long> idsVariantes, String jwtAcheteur) {
        List<StatutStock> statuts = stock.get()
                .uri(uri -> uri.path("/api/v1/stocks").queryParam("variantIds", idsVariantes).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAcheteur)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return statuts == null ? List.of() : statuts;
    }

    public List<Commande> commandes(String jwtAcheteur) {
        List<Commande> commandes = commande.get()
                .uri("/api/v1/commandes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAcheteur)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return commandes == null ? List.of() : commandes;
    }

    public Commande commande(long idCommande, String jwtAcheteur) {
        return commande.get()
                .uri("/api/v1/commandes/{id}", idCommande)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAcheteur)
                .retrieve()
                .body(Commande.class);
    }

    // ------------------------------------------------------------------ DTO (champs utiles seulement)

    public record PageProduits(List<ProduitResume> content) {
    }

    public record ProduitResume(Long id, String titre, BigDecimal prixTTC, boolean enStock,
                                String nomMarque, String nomSaison, String nomEquipe) {
    }

    public record ProduitDetail(Long id, String titre, String description, BigDecimal prixTTC,
                                BigDecimal prixApresPromotionTTC, String couleursPrincipales,
                                String impactEnvi, Boolean estFlocable, boolean enStock, String statut,
                                Marque marque, Nom saison, Nom equipe, List<Variante> variantes) {
        public record Marque(String nomMarque) {
        }

        public record Nom(String nom) {
        }

        public record Variante(Long id, String taille) {
        }
    }

    public record StatutStock(Long variantId, String status) {
    }

    public record Commande(Long idCommande, String statut, Instant dateCommande, List<Ligne> lignes,
                           BigDecimal totalTtc, String codeSuiviTransporteur) {
        public record Ligne(String titreProduit, String taille, int quantite, String statut,
                            BigDecimal totalTtc, Flocage flocage) {
        }

        public record Flocage(String texte, int numero) {
        }
    }
}
