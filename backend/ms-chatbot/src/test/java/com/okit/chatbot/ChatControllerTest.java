package com.okit.chatbot;

import com.okit.chatbot.assistant.Assistant;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    MockMvc mvc;

    // Aucun appel réel au LLM dans les tests
    @MockitoBean
    Assistant assistant;

    @Test
    void sansJetonRefuse() throws Exception {
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void vendeurRefuse() throws Exception {
        mvc.perform(post("/api/v1/chat").with(utilisateur("vendeur", "ROLE_SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acheteurObtientUneReponseAvecSonJetonEtSonHistorique() throws Exception {
        when(assistant.repondre(anyString(), anyList(), anyString())).thenReturn("Bonjour !");

        mvc.perform(post("/api/v1/chat").with(utilisateur("acheteur-1", "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "  Où en est ma commande ?  ",
                                 "historique": [{"role": "UTILISATEUR", "contenu": "Salut"},
                                                {"role": "ASSISTANT", "contenu": "Bonjour"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponse").value("Bonjour !"));

        verify(assistant).repondre(eq("Où en est ma commande ?"),
                eq(List.of(new Assistant.MessagePrecedent(Assistant.Role.UTILISATEUR, "Salut"),
                        new Assistant.MessagePrecedent(Assistant.Role.ASSISTANT, "Bonjour"))),
                eq("jeton-acheteur-1"));
    }

    @Test
    void messageVideRefuse() throws Exception {
        mvc.perform(post("/api/v1/chat").with(utilisateur("acheteur-2", "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assistant);
    }

    @Test
    void quotaJournalierParAcheteur() throws Exception {
        when(assistant.repondre(anyString(), anyList(), anyString())).thenReturn("ok");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/chat").with(utilisateur("acheteur-3", "ROLE_BUYER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/chat").with(utilisateur("acheteur-3", "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isTooManyRequests());
        // Un autre acheteur n'est pas impacté
        mvc.perform(post("/api/v1/chat").with(utilisateur("acheteur-4", "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void limiteDeLaPlateformeIaRenvoie429() throws Exception {
        when(assistant.repondre(anyString(), anyList(), anyString()))
                .thenThrow(new NonTransientAiException("HTTP 429 - {\"error\":\"Rate limit dépassé\"}"));
        mvc.perform(post("/api/v1/chat").with(utilisateur("acheteur-5", "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cleRefuseeParLaPlateformeIaRenvoie502() throws Exception {
        when(assistant.repondre(anyString(), anyList(), anyString()))
                .thenThrow(new NonTransientAiException("HTTP 401 - {\"error\":\"jeton invalide\"}"));
        mvc.perform(post("/api/v1/chat").with(utilisateur("acheteur-6", "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isBadGateway());
    }

    private static RequestPostProcessor utilisateur(String sub, String role) {
        return jwt().jwt(j -> j.subject(sub).tokenValue("jeton-" + sub))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
