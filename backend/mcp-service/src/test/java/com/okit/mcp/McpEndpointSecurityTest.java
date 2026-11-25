package com.okit.mcp;

import com.okit.mcp.client.OkitApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class McpEndpointSecurityTest {

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
              "protocolVersion":"2025-03-26","capabilities":{},
              "clientInfo":{"name":"okit-test","version":"1.0"}}}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    OkitApiClient apiClient;

    @Test
    void rejectsUnauthenticatedMcpRequest() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownBrowserOrigin() throws Exception {
        mockMvc.perform(post("/mcp")
                        .with(jwt().authorities(() -> "ROLE_BUYER"))
                        .header("Origin", "https://malicious.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAuthenticatedBuyerInitialization() throws Exception {
        mockMvc.perform(post("/mcp")
                        .with(jwt().authorities(() -> "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE))
                .andExpect(status().isOk());
    }
}
