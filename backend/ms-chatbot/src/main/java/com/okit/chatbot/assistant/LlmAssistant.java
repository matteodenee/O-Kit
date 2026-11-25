package com.okit.chatbot.assistant;

import com.okit.chatbot.mcp.OkitMcpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Assistant branché sur la plateforme IA Polytech (API compatible OpenAI).
 * Le LLM décide seul d'appeler les outils O'Kit ; Spring AI exécute les appels
 * et lui renvoie les résultats avant qu'il rédige la réponse finale.
 */
@Component
@ConditionalOnProperty(name = "okit.chatbot.mode", havingValue = "polytech")
public class LlmAssistant implements Assistant {

    private final ChatClient chatClient;
    private final OkitMcpClient mcpClient;

    static final String CLE_ABSENTE = "cle-non-configuree";

    public LlmAssistant(ChatClient.Builder builder, OkitMcpClient mcpClient,
                        @Value("classpath:prompts/systeme.txt") Resource promptSysteme,
                        @Value("${spring.ai.openai.api-key:}") String cle) throws IOException {
        // Échoue au démarrage plutôt qu'à la première question d'un acheteur.
        if (cle == null || cle.isBlank() || CLE_ABSENTE.equals(cle)) {
            throw new IllegalStateException(
                    "CHATBOT_MODE=polytech mais POLYTECH_API_KEY n'est pas définie (voir .env)");
        }
        this.chatClient = builder
                .defaultSystem(promptSysteme.getContentAsString(StandardCharsets.UTF_8))
                .build();
        this.mcpClient = mcpClient;
    }

    @Override
    public String repondre(String question, List<MessagePrecedent> historique, String jwtAcheteur) {
        try (OkitMcpClient.Session session = mcpClient.open(jwtAcheteur)) {
            return chatClient.prompt()
                    .messages(historique.stream().map(LlmAssistant::versMessage).toList())
                    .user(question)
                    .toolCallbacks(session.toolCallbacks())
                    .call()
                    .content();
        }
    }

    private static Message versMessage(MessagePrecedent m) {
        return m.role() == Role.ASSISTANT ? new AssistantMessage(m.contenu()) : new UserMessage(m.contenu());
    }
}
