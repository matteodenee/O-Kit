package com.okit.chatbot.assistant;

import com.okit.chatbot.client.OkitApiClient;
import com.okit.chatbot.tools.OkitTools;
import com.okit.chatbot.tools.OkitTools.FicheProduit;
import com.okit.chatbot.tools.OkitTools.ResultatProduit;
import com.okit.chatbot.tools.OkitTools.ResumeCommande;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assistant SIMULÉ, actif par défaut (okit.chatbot.mode=mock) : ne consomme aucune requête
 * de la plateforme IA. Il choisit l'outil par mots-clés au lieu de laisser le LLM décider,
 * mais appelle les vrais services O'Kit : pratique pour développer l'écran de chat
 * et vérifier l'intégration avec le catalogue, le stock et les commandes.
 */
@Component
@ConditionalOnProperty(name = "okit.chatbot.mode", havingValue = "mock", matchIfMissing = true)
public class MockAssistant implements Assistant {

    private static final Pattern NUMERO = Pattern.compile("\\b(\\d+)\\b");
    private static final Pattern MOT_COMMANDE = Pattern.compile("\\bcommandes?\\b");

    private final OkitApiClient api;

    public MockAssistant(OkitApiClient api) {
        this.api = api;
    }

    @Override
    public String repondre(String question, List<MessagePrecedent> historique, String jwtAcheteur) {
        OkitTools outils = new OkitTools(api, jwtAcheteur);
        String q = question.toLowerCase(Locale.ROOT);
        Long numero = premierNombre(q);

        if (q.contains("retour") || q.contains("rembours") || q.contains("annul")) {
            return "[simulation] Une commande CONFIRMEE ou EN_PREPARATION s'annule depuis « Mes commandes ». "
                    + "Après livraison, un retour est possible sous 30 jours (hors articles floqués).";
        }
        // Mot entier : « recommande » ne doit pas être pris pour « commande »
        if (MOT_COMMANDE.matcher(q).find()) {
            if (numero != null) {
                var c = outils.detailCommande(numero);
                return "[simulation] Commande n°%d : %s, total %s € TTC.".formatted(c.idCommande(), c.statut(), c.totalTtc());
            }
            List<ResumeCommande> commandes = outils.mesCommandes();
            if (commandes.isEmpty()) {
                return "[simulation] Vous n'avez encore passé aucune commande.";
            }
            return "[simulation] Vos dernières commandes :\n" + commandes.stream()
                    .map(c -> "- n°%d : %s, %s € TTC".formatted(c.numero(), c.statut(), c.totalTtc()))
                    .collect(Collectors.joining("\n"));
        }
        if (numero != null && (q.contains("produit") || q.contains("taille") || q.contains("stock"))) {
            FicheProduit p = outils.detailProduit(numero);
            return "[simulation] %s : %s € TTC. Tailles : %s.".formatted(p.titre(), p.prixTtc(), p.tailles().stream()
                    .map(t -> t.taille() + " (" + t.disponibilite() + ")")
                    .collect(Collectors.joining(", ")));
        }
        if (numero != null && (q.contains("similaire") || q.contains("pareil"))) {
            return listeProduits("Produits similaires", outils.produitsSimilaires(numero));
        }
        return listeProduits("Quelques produits qui pourraient vous plaire", outils.rechercherProduits(question, null));
    }

    private static String listeProduits(String titre, List<ResultatProduit> produits) {
        if (produits.isEmpty()) {
            return "[simulation] Je n'ai trouvé aucun produit correspondant.";
        }
        return "[simulation] " + titre + " :\n" + produits.stream().limit(3)
                .map(p -> "- %s (n°%d) : %s € TTC%s".formatted(p.titre(), p.id(), p.prixTtc(), p.enStock() ? "" : ", épuisé"))
                .collect(Collectors.joining("\n"));
    }

    private static Long premierNombre(String texte) {
        Matcher m = NUMERO.matcher(texte);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }
}
