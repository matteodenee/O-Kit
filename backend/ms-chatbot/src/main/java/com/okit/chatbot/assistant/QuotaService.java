package com.okit.chatbot.assistant;

import com.okit.chatbot.config.ChatbotProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite le nombre de questions par acheteur et par jour.
 * La clé de la plateforme IA est partagée par tout le groupe et plafonnée par jour :
 * sans cette limite, un seul utilisateur pourrait l'épuiser.
 * <p>
 * Compteur en mémoire : remis à zéro au redémarrage, non partagé entre plusieurs instances.
 * Suffisant pour le MVP (une seule instance de ms-chatbot).
 */
@Component
public class QuotaService {

    private final int limite;
    private final boolean actif;
    private final Clock clock;
    private final Map<String, AtomicInteger> compteurs = new ConcurrentHashMap<>();
    private volatile LocalDate jour;

    @Autowired
    public QuotaService(ChatbotProperties props) {
        this(props, Clock.systemDefaultZone());
    }

    QuotaService(ChatbotProperties props, Clock clock) {
        this.limite = props.questionsParJour();
        // Le mode simulé ne consomme rien sur la plateforme IA : pas de limite.
        this.actif = "polytech".equals(props.mode());
        this.clock = clock;
        this.jour = LocalDate.now(clock);
    }

    /** Réserve une question pour cet utilisateur ; false si sa limite du jour est atteinte. */
    public boolean consommer(String idUtilisateur) {
        if (!actif) {
            return true;
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        if (!aujourdhui.equals(jour)) {
            synchronized (this) {
                if (!aujourdhui.equals(jour)) {
                    compteurs.clear();
                    jour = aujourdhui;
                }
            }
        }
        return compteurs.computeIfAbsent(idUtilisateur, k -> new AtomicInteger()).incrementAndGet() <= limite;
    }
}
