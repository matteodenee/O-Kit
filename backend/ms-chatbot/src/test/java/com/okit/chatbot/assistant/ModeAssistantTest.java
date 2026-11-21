package com.okit.chatbot.assistant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vérifie que le bon assistant est câblé selon okit.chatbot.mode (aucun appel au LLM). */
class ModeAssistantTest {

    @Nested
    @SpringBootTest(properties = "okit.chatbot.mode=polytech")
    @ActiveProfiles("test")
    class ModePolytech {
        @Autowired
        Assistant assistant;

        @Test
        void utiliseLeVraiLlm() {
            assertThat(assistant).isInstanceOf(LlmAssistant.class);
        }
    }

    @Test
    void modePolytechSansCleRefuseDeDemarrer() {
        assertThatThrownBy(() -> new LlmAssistant(null, null, null, LlmAssistant.CLE_ABSENTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POLYTECH_API_KEY");
    }

    /** Le mode simulé doit démarrer sans aucune clé (valeur par défaut factice). */
    @Nested
    @SpringBootTest(properties = {"okit.chatbot.mode=mock", "spring.ai.openai.api-key=cle-non-configuree"})
    @ActiveProfiles("test")
    class ModeMock {
        @Autowired
        Assistant assistant;

        @Test
        void utiliseLaSimulation() {
            assertThat(assistant).isInstanceOf(MockAssistant.class);
        }
    }
}
