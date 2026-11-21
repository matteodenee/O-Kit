package com.okit.chatbot.api;

import com.okit.chatbot.assistant.Assistant;
import com.okit.chatbot.assistant.Assistant.MessagePrecedent;
import com.okit.chatbot.assistant.QuotaService;
import com.okit.chatbot.config.ChatbotProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Point d'entrée du chatbot. Le service est SANS ÉTAT : c'est le client (app mobile)
 * qui garde la conversation et renvoie les derniers messages à chaque question.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final Assistant assistant;
    private final QuotaService quota;
    private final ChatbotProperties props;

    public ChatController(Assistant assistant, QuotaService quota, ChatbotProperties props) {
        this.assistant = assistant;
        this.quota = quota;
        this.props = props;
    }

    @PostMapping
    public ReponseChat discuter(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuestionChat demande) {
        if (demande.message().length() > props.messageMax()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message trop long (" + props.messageMax() + " caractères max)");
        }
        if (!quota.consommer(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de questions atteinte pour aujourd'hui, réessayez demain");
        }
        List<MessagePrecedent> historique = demande.historique() == null ? List.of() : demande.historique();
        if (historique.size() > props.historiqueMax()) {
            historique = historique.subList(historique.size() - props.historiqueMax(), historique.size());
        }
        String reponse = assistant.repondre(demande.message().strip(), historique, jwt.getTokenValue());
        return new ReponseChat(reponse);
    }

    public record QuestionChat(@NotBlank String message, List<@Valid @NotNull MessagePrecedent> historique) {
    }

    public record ReponseChat(String reponse) {
    }
}
