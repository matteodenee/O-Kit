package com.okit.commande.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Valeurs lues dans application.yml, section "okit.commande". */
@ConfigurationProperties("okit.commande")
public record CommandeProperties(
        BigDecimal fraisLivraison,
        BigDecimal prixFlocageHt,
        int flocageTexteMax,
        Services services
) {
    public record Services(String catalogueUrl, String stockUrl) {
    }
}
