package com.okit.chatbot.assistant;

import com.okit.chatbot.client.OkitApiClient;
import com.okit.chatbot.client.OkitApiClient.ProduitResume;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MockAssistantTest {

    private final OkitApiClient api = mock(OkitApiClient.class);
    private final MockAssistant assistant = new MockAssistant(api);

    @Test
    void recommanderNEstPasConfonduAvecUneCommande() {
        when(api.produitsActifs()).thenReturn(List.of(
                new ProduitResume(13L, "Maillot Montpellier", new BigDecimal("85"), true, "Kappa", "2026", "MHSC")));

        String reponse = assistant.repondre("Recommande-moi un maillot à moins de 100 €", List.of(), "jwt");

        assertThat(reponse).contains("Maillot Montpellier");
        verify(api, never()).commande(anyLong(), anyString());
        verify(api, never()).commandes(anyString());
    }

    @Test
    void questionSurLesCommandesListeCellesDeLAcheteur() {
        when(api.commandes("jwt")).thenReturn(List.of());

        assertThat(assistant.repondre("Où en est ma dernière commande ?", List.of(), "jwt"))
                .contains("aucune commande");
    }
}
