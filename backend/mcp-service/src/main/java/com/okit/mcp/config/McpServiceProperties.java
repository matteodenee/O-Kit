package com.okit.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("okit.mcp")
public record McpServiceProperties(Services services, Security security) {

    public record Services(String catalogueUrl, String stockUrl, String commandeUrl) {
    }

    public record Security(List<String> allowedOrigins) {
        public Security {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }
}
