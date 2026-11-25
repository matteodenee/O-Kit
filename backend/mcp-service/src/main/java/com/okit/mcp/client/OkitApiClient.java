package com.okit.mcp.client;

import com.okit.mcp.config.McpServiceProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class OkitApiClient {

    private final RestClient catalogue;
    private final RestClient stock;
    private final RestClient commande;

    public OkitApiClient(RestClient.Builder builder, McpServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient.Builder base = builder.clone().requestFactory(factory);
        this.catalogue = base.clone().baseUrl(properties.services().catalogueUrl()).build();
        this.stock = base.clone().baseUrl(properties.services().stockUrl()).build();
        this.commande = base.clone().baseUrl(properties.services().commandeUrl()).build();
    }

    public List<ProductSummary> activeProducts() {
        ProductPage page = catalogue.get()
                .uri("/api/v1/products?statut=ACTIF&size=100")
                .retrieve()
                .body(ProductPage.class);
        return page == null || page.content() == null ? List.of() : page.content();
    }

    public ProductDetails product(long productId) {
        return catalogue.get()
                .uri("/api/v1/products/{id}", productId)
                .retrieve()
                .body(ProductDetails.class);
    }

    public List<StockStatus> stocks(List<Long> variantIds, String jwt) {
        List<StockStatus> statuses = stock.get()
                .uri(uri -> uri.path("/api/v1/stocks").queryParam("variantIds", variantIds).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return statuses == null ? List.of() : statuses;
    }

    public List<Order> orders(String jwt) {
        List<Order> orders = commande.get()
                .uri("/api/v1/commandes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return orders == null ? List.of() : orders;
    }

    public Order order(long orderId, String jwt) {
        return commande.get()
                .uri("/api/v1/commandes/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .retrieve()
                .body(Order.class);
    }

    public record ProductPage(List<ProductSummary> content) {
    }

    public record ProductSummary(Long id, String titre, BigDecimal prixTTC, boolean enStock,
                                 String nomMarque, String nomSaison, String nomEquipe) {
    }

    public record ProductDetails(Long id, String titre, String description, BigDecimal prixTTC,
                                 BigDecimal prixApresPromotionTTC, String couleursPrincipales,
                                 String impactEnvi, Boolean estFlocable, boolean enStock, String statut,
                                 Brand marque, Name saison, Name equipe, List<Variant> variantes) {
        public record Brand(String nomMarque) {
        }

        public record Name(String nom) {
        }

        public record Variant(Long id, String taille) {
        }
    }

    public record StockStatus(Long variantId, String status) {
    }

    public record Order(Long idCommande, String statut, Instant dateCommande, List<OrderLine> lignes,
                        BigDecimal totalTtc, String codeSuiviTransporteur) {
        public record OrderLine(String titreProduit, String taille, int quantite, String statut,
                                BigDecimal totalTtc, Personalization flocage) {
        }

        public record Personalization(String texte, int numero) {
        }
    }
}
