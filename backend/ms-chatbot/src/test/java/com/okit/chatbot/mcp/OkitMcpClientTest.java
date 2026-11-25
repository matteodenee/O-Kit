package com.okit.chatbot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okit.chatbot.config.ChatbotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OkitMcpClientTest {

    private final OkitMcpClient client = new OkitMcpClient(
            RestClient.builder(),
            new ObjectMapper(),
            new ChatbotProperties("mock", 3, 10, 1_000, "http://localhost/mcp"));

    @Test
    void decodesJsonResponse() {
        var response = client.decodeResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");

        assertThat(response.path("id").asInt()).isEqualTo(1);
    }

    @Test
    void decodesStreamableHttpSseResponseWithMetadataLines() {
        String response = """
                id:85dabe41-131f-42f6-8e3f-b96b9dd61b35
                event:message
                data:{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}

                """;

        assertThat(client.decodeResponse(response).path("result").path("tools").isArray()).isTrue();
    }
}
