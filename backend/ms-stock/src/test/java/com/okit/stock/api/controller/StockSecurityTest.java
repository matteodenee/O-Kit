package com.okit.stock.api.controller;

import com.okit.stock.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
class StockSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Endpoint client accessible avec le rôle BUYER")
    void testPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/stocks")
                        .param("variantIds", "1", "2")
                        .with(jwt().authorities(() -> "ROLE_BUYER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint vendeur refusé sans rôle SELLER (403)")
    void testSellerEndpointForbiddenForBuyer() throws Exception {
        mockMvc.perform(get("/api/v1/seller/stocks")
                        .with(jwt().authorities(() -> "ROLE_BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Endpoint interne refusé sans rôle SVC_COMMANDE (403)")
    void testInternalEndpointForbiddenForSeller() throws Exception {
        mockMvc.perform(post("/internal/stocks/decrement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"O-1\",\"lines\":[]}")
                        .with(jwt().authorities(() -> "ROLE_SELLER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Endpoint interne autorisé avec le rôle SVC_COMMANDE")
    void testInternalEndpointAllowedForService() throws Exception {
        mockMvc.perform(post("/internal/stocks/decrement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"O-1\",\"lines\":[{\"variantId\":1,\"quantity\":1}]}")
                        .with(jwt().authorities(() -> "ROLE_SVC_COMMANDE")))
                .andExpect(status().is4xxClientError());
    }
}