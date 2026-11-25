package com.okit.mcp.tools;

import com.okit.mcp.client.OkitApiClient;
import com.okit.mcp.client.OkitApiClient.Order;
import com.okit.mcp.client.OkitApiClient.ProductDetails;
import com.okit.mcp.client.OkitApiClient.ProductSummary;
import com.okit.mcp.client.OkitApiClient.StockStatus;
import com.okit.mcp.security.CurrentCaller;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OkitMcpTools {

    static final int MAX_RESULTS = 8;

    private final OkitApiClient api;
    private final CurrentCaller caller;

    public OkitMcpTools(OkitApiClient api, CurrentCaller caller) {
        this.api = api;
        this.caller = caller;
    }

    @Tool(name = "search_products", description = """
            Recherche des produits actifs O'Kit par mots-clés dans le titre, la marque, l'équipe ou la saison.
            Le prix TTC maximal est facultatif. Retourne au maximum huit résultats.""")
    public List<ProductResult> searchProducts(
            @ToolParam(description = "Mots-clés, par exemple 'maillot PSG'", required = false) String query,
            @ToolParam(description = "Prix TTC maximal en euros", required = false) BigDecimal maxPriceTtc) {
        caller.buyerOrSellerToken();
        List<String> words = words(query);
        return api.activeProducts().stream()
                .filter(product -> maxPriceTtc == null || product.prixTTC() == null
                        || product.prixTTC().compareTo(maxPriceTtc) <= 0)
                .map(product -> Map.entry(product, score(product, words)))
                .filter(entry -> words.isEmpty() || entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<ProductSummary, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> !entry.getKey().enStock()))
                .limit(MAX_RESULTS)
                .map(entry -> ProductResult.from(entry.getKey()))
                .toList();
    }

    @Tool(name = "get_product_details", description = """
            Consulte la fiche d'un produit actif : description, prix, promotion, marque, équipe, saison,
            flocage et disponibilité de chaque taille.""")
    public ProductSheet getProductDetails(
            @ToolParam(description = "Identifiant du produit") long productId) {
        String jwt = caller.buyerOrSellerToken();
        ProductDetails product = activeProduct(productId);
        List<ProductDetails.Variant> variants = product.variantes() == null ? List.of() : product.variantes();
        Map<Long, String> stockByVariant = variants.isEmpty() ? Map.of()
                : api.stocks(variants.stream().map(ProductDetails.Variant::id).toList(), jwt).stream()
                .collect(Collectors.toMap(StockStatus::variantId, StockStatus::status, (left, right) -> left));
        return new ProductSheet(
                product.id(), product.titre(), product.description(), product.prixTTC(),
                product.prixApresPromotionTTC(), product.couleursPrincipales(), product.impactEnvi(),
                Boolean.TRUE.equals(product.estFlocable()),
                product.marque() == null ? null : product.marque().nomMarque(),
                product.equipe() == null ? null : product.equipe().nom(),
                product.saison() == null ? null : product.saison().nom(),
                variants.stream()
                        .map(variant -> new SizeAvailability(variant.id(), variant.taille(),
                                translateStock(stockByVariant.get(variant.id()))))
                        .toList());
    }

    @Tool(name = "get_product_stock", description = """
            Consulte la disponibilité des variantes de produit demandées. Utiliser les identifiants de variantes
            retournés par get_product_details.""")
    public List<VariantStock> getProductStock(
            @ToolParam(description = "Identifiants de variantes", required = true) List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            throw new IllegalArgumentException("Au moins un identifiant de variante est requis");
        }
        if (variantIds.size() > 50) {
            throw new IllegalArgumentException("Cinquante variantes maximum par appel");
        }
        String jwt = caller.buyerOrSellerToken();
        return api.stocks(variantIds, jwt).stream()
                .map(stock -> new VariantStock(stock.variantId(), translateStock(stock.status())))
                .toList();
    }

    @Tool(name = "find_similar_products", description = """
            Recommande des produits actifs proches d'un produit donné : même équipe, puis même marque,
            en privilégiant ceux qui sont disponibles.""")
    public List<ProductResult> findSimilarProducts(
            @ToolParam(description = "Identifiant du produit de référence") long productId) {
        caller.buyerOrSellerToken();
        ProductDetails reference = activeProduct(productId);
        String team = reference.equipe() == null ? null : reference.equipe().nom();
        String brand = reference.marque() == null ? null : reference.marque().nomMarque();
        return api.activeProducts().stream()
                .filter(product -> !Objects.equals(product.id(), reference.id()))
                .filter(product -> (team != null && team.equals(product.nomEquipe()))
                        || (brand != null && brand.equals(product.nomMarque())))
                .sorted(Comparator.comparing((ProductSummary product) ->
                                !(team != null && team.equals(product.nomEquipe())))
                        .thenComparing(product -> !product.enStock()))
                .limit(MAX_RESULTS)
                .map(ProductResult::from)
                .toList();
    }

    @Tool(name = "list_my_orders", description = """
            Liste les dernières commandes hors panier de l'acheteur authentifié, avec statut, date, total et suivi.
            Cet outil est réservé au rôle BUYER.""")
    public List<OrderSummary> listMyOrders() {
        String jwt = caller.buyerToken();
        return api.orders(jwt).stream()
                .filter(order -> !"PANIER".equals(order.statut()))
                .sorted(Comparator.comparing(Order::dateCommande,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RESULTS)
                .map(OrderSummary::from)
                .toList();
    }

    @Tool(name = "get_order_status", description = """
            Consulte le statut et le détail synthétique d'une commande appartenant à l'acheteur authentifié.
            Une commande d'un autre acheteur reste introuvable. Cet outil est réservé au rôle BUYER.""")
    public OrderDetails getOrderStatus(
            @ToolParam(description = "Numéro de commande") long orderId) {
        String jwt = caller.buyerToken();
        try {
            Order order = api.order(orderId, jwt);
            if (order == null) {
                throw new IllegalArgumentException("Commande introuvable pour cet acheteur");
            }
            return OrderDetails.from(order);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden exception) {
            throw new IllegalArgumentException("Commande introuvable pour cet acheteur");
        }
    }

    public record ProductResult(Long id, String title, BigDecimal priceTtc, boolean inStock,
                                String brand, String team, String season) {
        static ProductResult from(ProductSummary product) {
            return new ProductResult(product.id(), product.titre(), product.prixTTC(), product.enStock(),
                    product.nomMarque(), product.nomEquipe(), product.nomSaison());
        }
    }

    public record ProductSheet(Long id, String title, String description, BigDecimal priceTtc,
                               BigDecimal promotionalPriceTtc, String colors, String environmentalImpact,
                               boolean customizable, String brand, String team, String season,
                               List<SizeAvailability> sizes) {
    }

    public record SizeAvailability(Long variantId, String size, String availability) {
    }

    public record VariantStock(Long variantId, String availability) {
    }

    public record OrderSummary(Long number, String status, Instant date, BigDecimal totalTtc,
                               int itemCount, String carrierTrackingCode) {
        static OrderSummary from(Order order) {
            int count = order.lignes() == null ? 0
                    : order.lignes().stream().mapToInt(Order.OrderLine::quantite).sum();
            return new OrderSummary(order.idCommande(), order.statut(), order.dateCommande(), order.totalTtc(),
                    count, order.codeSuiviTransporteur());
        }
    }

    public record OrderDetails(Long number, String status, Instant date, BigDecimal totalTtc,
                               String carrierTrackingCode, List<Order.OrderLine> items) {
        static OrderDetails from(Order order) {
            return new OrderDetails(order.idCommande(), order.statut(), order.dateCommande(), order.totalTtc(),
                    order.codeSuiviTransporteur(), order.lignes() == null ? List.of() : order.lignes());
        }
    }

    private ProductDetails activeProduct(long productId) {
        try {
            ProductDetails product = api.product(productId);
            if (product == null || !"ACTIF".equals(product.statut())) {
                throw new IllegalArgumentException("Produit " + productId + " introuvable");
            }
            return product;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new IllegalArgumentException("Produit " + productId + " introuvable");
        }
    }

    private static String translateStock(String status) {
        if (status == null) {
            return "inconnue";
        }
        return switch (status) {
            case "IN_STOCK" -> "en stock";
            case "LOW_STOCK" -> "stock faible";
            case "OUT_OF_STOCK" -> "épuisé";
            default -> "inconnue";
        };
    }

    static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalize(text).split("[^a-z0-9]+"))
                .filter(word -> word.length() >= 2)
                .distinct()
                .toList();
    }

    private static long score(ProductSummary product, List<String> words) {
        String text = normalize(Stream.of(product.titre(), product.nomMarque(), product.nomEquipe(),
                        product.nomSaison())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" ")));
        return words.stream().filter(text::contains).count();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
