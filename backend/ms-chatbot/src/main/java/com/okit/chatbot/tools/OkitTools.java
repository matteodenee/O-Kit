package com.okit.chatbot.tools;

import com.okit.chatbot.client.OkitApiClient;
import com.okit.chatbot.client.OkitApiClient.Commande;
import com.okit.chatbot.client.OkitApiClient.ProduitDetail;
import com.okit.chatbot.client.OkitApiClient.ProduitResume;
import com.okit.chatbot.client.OkitApiClient.StatutStock;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Outils que le LLM peut appeler. Une instance est créée PAR REQUÊTE avec le JWT de l'acheteur,
 * pour que les appels au stock et aux commandes se fassent avec ses droits à lui.
 * <p>
 * Les réponses sont volontairement réduites (peu de champs, peu de résultats) :
 * chaque caractère renvoyé au LLM consomme des tokens.
 */
public class OkitTools {

    static final int MAX_RESULTATS = 8;

    private final OkitApiClient api;
    private final String jwtAcheteur;

    public OkitTools(OkitApiClient api, String jwtAcheteur) {
        this.api = api;
        this.jwtAcheteur = jwtAcheteur;
    }

    // ------------------------------------------------------------------ produits

    @Tool(description = """
            Recherche des produits actifs du catalogue O'Kit (maillots, équipements...) par mots-clés
            portant sur le titre, la marque, l'équipe ou la saison. Sert aussi à recommander des produits.
            Laisser 'recherche' vide pour lister des produits.""")
    public List<ResultatProduit> rechercherProduits(
            @ToolParam(description = "mots-clés, ex. 'maillot PSG domicile'", required = false) String recherche,
            @ToolParam(description = "prix TTC maximum en euros", required = false) BigDecimal prixMaxTtc) {
        List<String> mots = mots(recherche);
        return api.produitsActifs().stream()
                .filter(p -> prixMaxTtc == null || p.prixTTC() == null || p.prixTTC().compareTo(prixMaxTtc) <= 0)
                .map(p -> Map.entry(p, score(p, mots)))
                .filter(e -> mots.isEmpty() || e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<ProduitResume, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(e -> !e.getKey().enStock()))
                .limit(MAX_RESULTATS)
                .map(e -> ResultatProduit.from(e.getKey()))
                .toList();
    }

    @Tool(description = """
            Donne la fiche complète d'un produit : description, prix, promotion, couleurs, flocage possible
            et disponibilité de chaque taille.""")
    public FicheProduit detailProduit(@ToolParam(description = "identifiant du produit") long idProduit) {
        ProduitDetail p = produitOuErreur(idProduit);
        List<ProduitDetail.Variante> variantes = p.variantes() == null ? List.of() : p.variantes();
        Map<Long, String> stockParVariante = variantes.isEmpty() ? Map.of()
                : api.stocks(variantes.stream().map(ProduitDetail.Variante::id).toList(), jwtAcheteur).stream()
                .collect(Collectors.toMap(StatutStock::variantId, StatutStock::status, (a, b) -> a));
        return new FicheProduit(
                p.id(), p.titre(), p.description(), p.prixTTC(), p.prixApresPromotionTTC(),
                p.couleursPrincipales(), p.impactEnvi(), Boolean.TRUE.equals(p.estFlocable()),
                p.marque() == null ? null : p.marque().nomMarque(),
                p.equipe() == null ? null : p.equipe().nom(),
                p.saison() == null ? null : p.saison().nom(),
                variantes.stream()
                        .map(v -> new Taille(v.taille(), traduireStock(stockParVariante.get(v.id()))))
                        .toList());
    }

    @Tool(description = """
            Recommande des produits proches d'un produit donné (même équipe, puis même marque),
            en privilégiant ceux en stock.""")
    public List<ResultatProduit> produitsSimilaires(@ToolParam(description = "identifiant du produit") long idProduit) {
        ProduitDetail ref = produitOuErreur(idProduit);
        String equipe = ref.equipe() == null ? null : ref.equipe().nom();
        String marque = ref.marque() == null ? null : ref.marque().nomMarque();
        return api.produitsActifs().stream()
                .filter(p -> !Objects.equals(p.id(), ref.id()))
                .filter(p -> (equipe != null && equipe.equals(p.nomEquipe()))
                        || (marque != null && marque.equals(p.nomMarque())))
                .sorted(Comparator.comparing((ProduitResume p) -> !(equipe != null && equipe.equals(p.nomEquipe())))
                        .thenComparing(p -> !p.enStock()))
                .limit(MAX_RESULTATS)
                .map(ResultatProduit::from)
                .toList();
    }

    // ------------------------------------------------------------------ commandes de l'acheteur

    @Tool(description = "Liste les commandes de l'acheteur connecté (statut, date, total, suivi).")
    public List<ResumeCommande> mesCommandes() {
        return api.commandes(jwtAcheteur).stream()
                .filter(c -> !"PANIER".equals(c.statut()))
                .sorted(Comparator.comparing(Commande::dateCommande,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RESULTATS)
                .map(ResumeCommande::from)
                .toList();
    }

    @Tool(description = "Détail d'une commande de l'acheteur connecté : articles, tailles, flocages, statut de chaque ligne.")
    public Commande detailCommande(@ToolParam(description = "numéro de la commande") long idCommande) {
        try {
            return api.commande(idCommande, jwtAcheteur);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            throw new IllegalArgumentException("Commande " + idCommande + " introuvable pour cet acheteur");
        }
    }

    // ------------------------------------------------------------------ réponses envoyées au LLM

    public record ResultatProduit(Long id, String titre, BigDecimal prixTtc, boolean enStock,
                                  String marque, String equipe, String saison) {
        static ResultatProduit from(ProduitResume p) {
            return new ResultatProduit(p.id(), p.titre(), p.prixTTC(), p.enStock(),
                    p.nomMarque(), p.nomEquipe(), p.nomSaison());
        }
    }

    public record FicheProduit(Long id, String titre, String description, BigDecimal prixTtc,
                               BigDecimal prixPromoTtc, String couleurs, String impactEnvironnemental,
                               boolean flocable, String marque, String equipe, String saison,
                               List<Taille> tailles) {
    }

    public record Taille(String taille, String disponibilite) {
    }

    public record ResumeCommande(Long numero, String statut, Instant date, BigDecimal totalTtc,
                                 int nbArticles, String suiviTransporteur) {
        static ResumeCommande from(Commande c) {
            int nb = c.lignes() == null ? 0 : c.lignes().stream().mapToInt(Commande.Ligne::quantite).sum();
            return new ResumeCommande(c.idCommande(), c.statut(), c.dateCommande(), c.totalTtc(), nb,
                    c.codeSuiviTransporteur());
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private ProduitDetail produitOuErreur(long idProduit) {
        try {
            ProduitDetail p = api.produit(idProduit);
            if (p == null || !"ACTIF".equals(p.statut())) {
                throw new IllegalArgumentException("Produit " + idProduit + " introuvable");
            }
            return p;
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Produit " + idProduit + " introuvable");
        }
    }

    private static String traduireStock(String statut) {
        if (statut == null) {
            return "inconnue";
        }
        return switch (statut) {
            case "IN_STOCK" -> "en stock";
            case "LOW_STOCK" -> "stock faible";
            case "OUT_OF_STOCK" -> "épuisé";
            default -> "inconnue";
        };
    }

    static List<String> mots(String texte) {
        if (texte == null || texte.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normaliser(texte).split("[^a-z0-9]+"))
                .filter(m -> m.length() >= 2)
                .distinct()
                .toList();
    }

    private static long score(ProduitResume p, List<String> mots) {
        String texte = normaliser(Stream.of(p.titre(), p.nomMarque(), p.nomEquipe(), p.nomSaison())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" ")));
        return mots.stream().filter(texte::contains).count();
    }

    private static String normaliser(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
