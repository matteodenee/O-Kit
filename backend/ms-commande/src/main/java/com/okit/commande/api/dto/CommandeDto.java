package com.okit.commande.api.dto;

import com.okit.commande.persistence.entity.AdresseLivraison;
import com.okit.commande.persistence.entity.Commande;
import com.okit.commande.persistence.entity.Flocage;
import com.okit.commande.persistence.entity.LigneCommande;
import com.okit.commande.persistence.entity.StatutCommande;
import com.okit.commande.persistence.entity.StatutLigne;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Réponse JSON pour un panier ou une commande (même forme). */
public record CommandeDto(
        Long idCommande,
        StatutCommande statut,
        Instant dateCommande,
        List<LigneDto> lignes,
        BigDecimal prixHt,
        BigDecimal fraisLivraison,
        BigDecimal totalTtc,
        AdresseDto adresseLivraison,
        String codeSuiviTransporteur,
        String idTransaction
) {

    public record LigneDto(
            Long idLigneCommande,
            Long idProduit,
            Long idVariante,
            String titreProduit,
            String taille,
            int quantite,
            BigDecimal prixUnitaireHt,
            BigDecimal tauxTva,
            StatutLigne statut,
            FlocageDto flocage,
            BigDecimal totalHt,
            BigDecimal totalTtc
    ) {
    }

    public record FlocageDto(Long idFlocage, String texte, int numero, BigDecimal prix) {
    }

    public record AdresseDto(String rue, String complement, String codePostal, String ville) {
    }

    // ------------------------------------------------------------ conversion entité -> DTO

    public static CommandeDto from(Commande c) {
        return new CommandeDto(
                c.getId(),
                c.getStatut(),
                c.getDateCommande(),
                c.getLignes().stream().map(CommandeDto::from).toList(),
                c.getPrixHt(),
                c.lignesActives().isEmpty() ? BigDecimal.ZERO : c.getFraisLivraison(),
                c.totalTtc(),
                from(c.getAdresseLivraison()),
                c.getCodeSuiviTransporteur(),
                c.getIdTransaction());
    }

    private static LigneDto from(LigneCommande l) {
        Flocage f = l.getFlocage();
        return new LigneDto(
                l.getId(), l.getIdProduit(), l.getIdVariante(), l.getTitreProduit(), l.getTaille(),
                l.getQuantite(), l.getPrixUnitaireHt(), l.getTauxTva(), l.getStatut(),
                f == null ? null : new FlocageDto(f.getId(), f.getTexte(), f.getNumero(), f.getPrix()),
                l.totalHt(), l.totalTtc());
    }

    private static AdresseDto from(AdresseLivraison a) {
        return a == null ? null : new AdresseDto(a.getRue(), a.getComplement(), a.getCodePostal(), a.getVille());
    }
}
