package com.okit.chatbot.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Répond à une question de l'acheteur. Deux implémentations : vrai LLM ou simulation. */
public interface Assistant {

    String repondre(String question, List<MessagePrecedent> historique, String jwtAcheteur);

    /** Un message déjà échangé, renvoyé par le client pour garder le fil de la conversation. */
    record MessagePrecedent(@NotNull Role role, @NotBlank String contenu) {
    }

    enum Role { UTILISATEUR, ASSISTANT }
}
