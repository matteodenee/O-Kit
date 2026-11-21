package com.okit.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Valeurs lues dans application.yml, section "okit.chatbot". */
@ConfigurationProperties("okit.chatbot")
public record ChatbotProperties(
        String mode,
        int questionsParJour,
        int historiqueMax,
        int messageMax,
        Services services
) {
    public record Services(String catalogueUrl, String stockUrl, String commandeUrl) {
    }
}
