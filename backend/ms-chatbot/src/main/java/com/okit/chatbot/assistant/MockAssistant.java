package com.okit.chatbot.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.okit.chatbot.mcp.OkitMcpClient;
import com.okit.chatbot.mcp.OkitMcpClient.OrderDetails;
import com.okit.chatbot.mcp.OkitMcpClient.OrderSummary;
import com.okit.chatbot.mcp.OkitMcpClient.ProductResult;
import com.okit.chatbot.mcp.OkitMcpClient.ProductSheet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assistant SIMULÉ, actif par défaut (okit.chatbot.mode=mock) : ne consomme aucune requête
 * de la plateforme IA. Il choisit l'outil par mots-clés au lieu de laisser le LLM décider,
 * mais appelle les vrais services O'Kit : pratique pour développer l'écran de chat
 * et vérifier l'intégration avec le catalogue, le stock et les commandes.
 */
@Component
@ConditionalOnProperty(name = "okit.chatbot.mode", havingValue = "mock", matchIfMissing = true)
public class MockAssistant implements Assistant {

    private static final Pattern NUMERO = Pattern.compile("\\b(\\d+)\\b");
    private static final Pattern MOT_COMMANDE = Pattern.compile("\\bcommandes?\\b");

    private final OkitMcpClient mcpClient;

    public MockAssistant(OkitMcpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String repondre(String question, List<MessagePrecedent> historique, String jwtAcheteur) {
        String q = question.toLowerCase(Locale.ROOT);
        Long numero = premierNombre(q);

        if (q.contains("retour") || q.contains("rembours") || q.contains("annul")) {
            return "[simulation] Une commande CONFIRMEE ou EN_PREPARATION s'annule depuis « Mes commandes ». "
                    + "Après livraison, un retour est possible sous 30 jours (hors articles floqués).";
        }

        try (OkitMcpClient.Session session = mcpClient.open(jwtAcheteur)) {
            // Mot entier : « recommande » ne doit pas être pris pour « commande »
            if (MOT_COMMANDE.matcher(q).find()) {
                if (numero != null) {
                    OrderDetails order = session.call("get_order_status", Map.of("orderId", numero),
                            OrderDetails.class);
                    return "[simulation] Commande n°%d : %s, total %s € TTC."
                            .formatted(order.number(), order.status(), order.totalTtc());
                }
                List<OrderSummary> orders = session.call("list_my_orders", Map.of(), new TypeReference<>() {
                });
                if (orders.isEmpty()) {
                    return "[simulation] Vous n'avez encore passé aucune commande.";
                }
                return "[simulation] Vos dernières commandes :\n" + orders.stream()
                        .map(order -> "- n°%d : %s, %s € TTC"
                                .formatted(order.number(), order.status(), order.totalTtc()))
                        .collect(Collectors.joining("\n"));
            }
            if (numero != null && (q.contains("produit") || q.contains("taille") || q.contains("stock"))) {
                ProductSheet product = session.call("get_product_details", Map.of("productId", numero),
                        ProductSheet.class);
                return "[simulation] %s : %s € TTC. Tailles : %s."
                        .formatted(product.title(), product.priceTtc(), product.sizes().stream()
                                .map(size -> size.size() + " (" + size.availability() + ")")
                                .collect(Collectors.joining(", ")));
            }
            if (numero != null && (q.contains("similaire") || q.contains("pareil"))) {
                List<ProductResult> products = session.call("find_similar_products", Map.of("productId", numero),
                        new TypeReference<>() {
                        });
                return listeProduits("Produits similaires", products);
            }
            List<ProductResult> products = session.call("search_products", Map.of("query", question),
                    new TypeReference<>() {
                    });
            return listeProduits("Quelques produits qui pourraient vous plaire", products);
        }
    }

    private static String listeProduits(String titre, List<ProductResult> produits) {
        if (produits.isEmpty()) {
            return "[simulation] Je n'ai trouvé aucun produit correspondant.";
        }
        return "[simulation] " + titre + " :\n" + produits.stream().limit(3)
                .map(product -> "- %s (n°%d) : %s € TTC%s".formatted(product.title(), product.id(),
                        product.priceTtc(), product.inStock() ? "" : ", épuisé"))
                .collect(Collectors.joining("\n"));
    }

    private static Long premierNombre(String texte) {
        Matcher m = NUMERO.matcher(texte);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }
}
