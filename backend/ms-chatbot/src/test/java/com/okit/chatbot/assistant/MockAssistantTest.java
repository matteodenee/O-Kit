package com.okit.chatbot.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.okit.chatbot.mcp.OkitMcpClient;
import com.okit.chatbot.mcp.OkitMcpClient.ProductResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MockAssistantTest {

    private final OkitMcpClient mcpClient = mock(OkitMcpClient.class);
    private final OkitMcpClient.Session session = mock(OkitMcpClient.Session.class);
    private final MockAssistant assistant = new MockAssistant(mcpClient);

    MockAssistantTest() {
        when(mcpClient.open("jwt")).thenReturn(session);
    }

    @Test
    void recommanderNEstPasConfonduAvecUneCommande() {
        when(session.call(eq("search_products"), anyMap(), ArgumentMatchers.<TypeReference<List<ProductResult>>>any()))
                .thenReturn(List.of(new ProductResult(13L, "Maillot Montpellier", new BigDecimal("85"),
                        true, "Kappa", "MHSC", "2026")));

        String reponse = assistant.repondre("Recommande-moi un maillot à moins de 100 €", List.of(), "jwt");

        assertThat(reponse).contains("Maillot Montpellier");
        verify(session, never()).call(eq("get_order_status"), anyMap(), eq(OkitMcpClient.OrderDetails.class));
        verify(session, never()).call(eq("list_my_orders"), anyMap(), ArgumentMatchers.<TypeReference<List<OkitMcpClient.OrderSummary>>>any());
    }

    @Test
    void questionSurLesCommandesListeCellesDeLAcheteur() {
        when(session.call(eq("list_my_orders"), anyMap(),
                ArgumentMatchers.<TypeReference<List<OkitMcpClient.OrderSummary>>>any())).thenReturn(List.of());

        assertThat(assistant.repondre("Où en est ma dernière commande ?", List.of(), "jwt"))
                .contains("aucune commande");
    }

    @Test
    void politiqueDeRetourNeDependPasDuServeurMcp() {
        assertThat(assistant.repondre("Comment faire un retour ?", List.of(), "jwt"))
                .contains("30 jours");

        verify(mcpClient, never()).open(anyString());
    }
}
