package com.okit.chatbot.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okit.chatbot.config.ChatbotProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Client MCP Streamable HTTP minimal, avec une session et le JWT propres à chaque requête de chat. */
@Component
public class OkitMcpClient {

    private static final String MCP_SESSION_ID = "Mcp-Session-Id";
    private static final String MCP_PROTOCOL_VERSION = "MCP-Protocol-Version";
    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String endpoint;

    public OkitMcpClient(RestClient.Builder builder, ObjectMapper objectMapper, ChatbotProperties properties) {
        this.http = builder.build();
        this.objectMapper = objectMapper;
        this.endpoint = properties.mcpUrl();
    }

    public Session open(String jwt) {
        Map<String, Object> params = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "okit-chatbot", "version", "0.1.0"));
        var response = http.post()
                .uri(endpoint)
                .headers(headers -> protocolHeaders(headers, jwt, null))
                .body(request(1, "initialize", params))
                .retrieve()
                .toEntity(String.class);
        assertSuccessful(decodeResponse(response.getBody()));
        String sessionId = response.getHeaders().getFirst(MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("Le serveur MCP n'a pas retourné de session");
        }
        http.post()
                .uri(endpoint)
                .headers(headers -> protocolHeaders(headers, jwt, sessionId))
                .body(notification("notifications/initialized", Map.of()))
                .retrieve()
                .toBodilessEntity();
        return new Session(jwt, sessionId);
    }

    private static void protocolHeaders(HttpHeaders headers, String jwt, String sessionId) {
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set(MCP_PROTOCOL_VERSION, PROTOCOL_VERSION);
        if (sessionId != null) {
            headers.set(MCP_SESSION_ID, sessionId);
        }
    }

    private static Map<String, Object> request(long id, String method, Object params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        return request;
    }

    private static Map<String, Object> notification(String method, Object params) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);
        return notification;
    }

    private static void assertSuccessful(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("Réponse MCP vide");
        }
        if (response.has("error")) {
            throw new IllegalStateException("Erreur MCP : " + response.path("error").path("message").asText());
        }
    }

    JsonNode decodeResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Réponse MCP vide");
        }
        String payload = body.stripLeading();
        if (payload.startsWith("{") || payload.startsWith("[")) {
            return readJson(payload);
        }

        StringBuilder eventData = new StringBuilder();
        for (String line : payload.lines().toList()) {
            if (line.startsWith("data:")) {
                if (!eventData.isEmpty()) {
                    eventData.append('\n');
                }
                eventData.append(line.substring("data:".length()).stripLeading());
            } else if (line.isBlank() && !eventData.isEmpty()) {
                return readJson(eventData.toString());
            }
        }
        if (!eventData.isEmpty()) {
            return readJson(eventData.toString());
        }
        throw new IllegalStateException("Réponse MCP SSE sans événement data");
    }

    private JsonNode readJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Réponse MCP invalide", exception);
        }
    }

    public final class Session implements AutoCloseable {

        private final String jwt;
        private final String sessionId;
        private final AtomicLong nextId = new AtomicLong(2);

        private Session(String jwt, String sessionId) {
            this.jwt = jwt;
            this.sessionId = sessionId;
        }

        public ToolCallback[] toolCallbacks() {
            JsonNode response = exchange("tools/list", Map.of());
            List<ToolCallback> callbacks = new ArrayList<>();
            for (JsonNode tool : response.path("result").path("tools")) {
                ToolDefinition definition = ToolDefinition.builder()
                        .name(tool.path("name").asText())
                        .description(tool.path("description").asText())
                        .inputSchema(tool.path("inputSchema").toString())
                        .build();
                callbacks.add(new RemoteToolCallback(definition));
            }
            return callbacks.toArray(ToolCallback[]::new);
        }

        public <T> T call(String toolName, Map<String, Object> arguments, Class<T> responseType) {
            try {
                return objectMapper.readValue(callRaw(toolName, arguments), responseType);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Réponse MCP invalide pour " + toolName, exception);
            }
        }

        public <T> T call(String toolName, Map<String, Object> arguments, TypeReference<T> responseType) {
            try {
                return objectMapper.readValue(callRaw(toolName, arguments), responseType);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Réponse MCP invalide pour " + toolName, exception);
            }
        }

        public String callRaw(String toolName, Map<String, Object> arguments) {
            JsonNode response = exchange("tools/call", Map.of("name", toolName, "arguments", arguments));
            JsonNode result = response.path("result");
            if (result.path("isError").asBoolean(false)) {
                throw new IllegalArgumentException(contentText(result));
            }
            return contentText(result);
        }

        private JsonNode exchange(String method, Object params) {
            String body = http.post()
                    .uri(endpoint)
                    .headers(headers -> protocolHeaders(headers, jwt, sessionId))
                    .body(request(nextId.getAndIncrement(), method, params))
                    .retrieve()
                    .body(String.class);
            JsonNode response = decodeResponse(body);
            assertSuccessful(response);
            return response;
        }

        private static String contentText(JsonNode result) {
            StringBuilder text = new StringBuilder();
            for (JsonNode content : result.path("content")) {
                if (content.path("type").asText().equals("text")) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(content.path("text").asText());
                }
            }
            if (text.isEmpty()) {
                throw new IllegalStateException("Le résultat MCP ne contient aucun texte");
            }
            return text.toString();
        }

        @Override
        public void close() {
            try {
                http.delete()
                        .uri(endpoint)
                        .headers(headers -> protocolHeaders(headers, jwt, sessionId))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException ignored) {
                // La réponse du chatbot est déjà calculée ; la session expirera côté serveur.
            }
        }

        private final class RemoteToolCallback implements ToolCallback {

            private final ToolDefinition definition;

            private RemoteToolCallback(ToolDefinition definition) {
                this.definition = definition;
            }

            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                try {
                    JsonNode input = objectMapper.readTree(toolInput);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = objectMapper.convertValue(input, Map.class);
                    return callRaw(definition.name(), arguments);
                } catch (JsonProcessingException exception) {
                    throw new IllegalArgumentException("Arguments invalides pour " + definition.name(), exception);
                }
            }
        }
    }

    public record ProductResult(Long id, String title, BigDecimal priceTtc, boolean inStock,
                                String brand, String team, String season) {
    }

    public record SizeAvailability(Long variantId, String size, String availability) {
    }

    public record ProductSheet(Long id, String title, String description, BigDecimal priceTtc,
                               BigDecimal promotionalPriceTtc, String colors, String environmentalImpact,
                               boolean customizable, String brand, String team, String season,
                               List<SizeAvailability> sizes) {
    }

    public record OrderSummary(Long number, String status, Instant date, BigDecimal totalTtc,
                               int itemCount, String carrierTrackingCode) {
    }

    public record OrderDetails(Long number, String status, Instant date, BigDecimal totalTtc,
                               String carrierTrackingCode, List<JsonNode> items) {
    }
}
