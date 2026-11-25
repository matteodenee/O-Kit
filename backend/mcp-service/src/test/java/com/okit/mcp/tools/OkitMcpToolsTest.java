package com.okit.mcp.tools;

import com.okit.mcp.client.OkitApiClient;
import com.okit.mcp.client.OkitApiClient.ProductDetails;
import com.okit.mcp.client.OkitApiClient.ProductSummary;
import com.okit.mcp.client.OkitApiClient.StockStatus;
import com.okit.mcp.security.CurrentCaller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OkitMcpToolsTest {

    private final OkitApiClient api = mock(OkitApiClient.class);
    private final OkitMcpTools tools = new OkitMcpTools(api, new CurrentCaller());

    @BeforeEach
    void authenticateBuyer() {
        authenticate("ROLE_BUYER", "buyer-token");
        when(api.activeProducts()).thenReturn(List.of(
                summary(1L, "Maillot domicile 2026", "90", true, "Nike", "PSG"),
                summary(2L, "Maillot extérieur 2026", "85", false, "Nike", "PSG"),
                summary(3L, "Maillot domicile 2026", "80", true, "Puma", "OM")));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesProductsWithoutAccentAndWithMaximumPrice() {
        assertThat(tools.searchProducts("MAILLOT exterieur psg", new BigDecimal("85")))
                .extracting(OkitMcpTools.ProductResult::id)
                .containsExactly(2L, 3L);
    }

    @Test
    void returnsProductDetailsWithRealStock() {
        when(api.product(1L)).thenReturn(details(1L));
        when(api.stocks(List.of(11L, 12L), "buyer-token")).thenReturn(List.of(
                new StockStatus(11L, "IN_STOCK"), new StockStatus(12L, "OUT_OF_STOCK")));

        OkitMcpTools.ProductSheet sheet = tools.getProductDetails(1L);

        assertThat(sheet.sizes()).containsExactly(
                new OkitMcpTools.SizeAvailability(11L, "M", "en stock"),
                new OkitMcpTools.SizeAvailability(12L, "L", "épuisé"));
        verify(api).stocks(List.of(11L, 12L), "buyer-token");
    }

    @Test
    void relaysBuyerTokenWhenReadingOwnOrder() {
        when(api.order(42L, "buyer-token")).thenReturn(new OkitApiClient.Order(
                42L, "EXPEDIEE", Instant.parse("2026-11-20T10:15:30Z"), List.of(),
                new BigDecimal("99.90"), "TRACK-42"));

        assertThat(tools.getOrderStatus(42L).status()).isEqualTo("EXPEDIEE");
        verify(api).order(42L, "buyer-token");
    }

    @Test
    void sellerCannotReadBuyerOrders() {
        authenticate("ROLE_SELLER", "seller-token");

        assertThatThrownBy(() -> tools.getOrderStatus(42L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(String authority, String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .subject("subject-1")
                .claim("realm_access", java.util.Map.of("roles", List.of(authority.substring(5))))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
    }

    private static ProductSummary summary(long id, String title, String price, boolean inStock,
                                          String brand, String team) {
        return new ProductSummary(id, title, new BigDecimal(price), inStock, brand, "2026", team);
    }

    private static ProductDetails details(long id) {
        return new ProductDetails(id, "Maillot PSG", "Description", new BigDecimal("90"), null,
                "bleu", "A", true, true, "ACTIF", new ProductDetails.Brand("Nike"),
                new ProductDetails.Name("2026"), new ProductDetails.Name("PSG"),
                List.of(new ProductDetails.Variant(11L, "M"), new ProductDetails.Variant(12L, "L")));
    }
}
