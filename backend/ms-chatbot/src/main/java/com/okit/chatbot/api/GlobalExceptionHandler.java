package com.okit.chatbot.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Produit ou commande introuvable (surtout en mode simulé, où l'outil est appelé directement). */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail introuvable(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** La plateforme IA a refusé la requête (clé invalide, quota du jour épuisé...). */
    @ExceptionHandler(HttpClientErrorException.class)
    ProblemDetail refusAmont(HttpClientErrorException e) {
        log.warn("Appel refusé par un service amont : {} {}", e.getStatusCode(), e.getResponseBodyAsString());
        HttpStatus statut = e.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        return ProblemDetail.forStatusAndDetail(statut, "L'assistant est momentanément indisponible");
    }

    /** La plateforme IA a refusé la requête (Spring AI) : quota épuisé (429), clé invalide (401/403)... */
    @ExceptionHandler(NonTransientAiException.class)
    ProblemDetail refusPlateformeIa(NonTransientAiException e) {
        log.warn("Plateforme IA : {}", e.getMessage());
        if (e.getMessage() != null && e.getMessage().startsWith("HTTP 429")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "L'assistant a atteint sa limite d'utilisation, réessayez plus tard");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "L'assistant est momentanément indisponible");
    }

    /** Erreur temporaire de la plateforme IA (5xx, coupure) après les nouvelles tentatives de Spring AI. */
    @ExceptionHandler(TransientAiException.class)
    ProblemDetail plateformeIaIndisponible(TransientAiException e) {
        log.warn("Plateforme IA indisponible : {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "L'assistant est momentanément indisponible");
    }

    /** Service O'Kit ou plateforme IA injoignable. */
    @ExceptionHandler(RestClientException.class)
    ProblemDetail indisponible(RestClientException e) {
        log.warn("Service amont injoignable", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "L'assistant est momentanément indisponible");
    }
}
