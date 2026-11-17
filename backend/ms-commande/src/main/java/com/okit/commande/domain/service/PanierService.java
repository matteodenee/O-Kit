package com.okit.commande.domain.service;

import com.okit.commande.api.dto.CommandeDto;
import com.okit.commande.api.dto.Requetes.AjoutLigne;
import com.okit.commande.api.dto.Requetes.FlocageDemande;
import com.okit.commande.api.dto.Requetes.ModifierQuantite;
import com.okit.commande.client.CatalogueClient;
import com.okit.commande.client.CatalogueClient.ProduitCatalogue;
import com.okit.commande.client.StockClient;
import com.okit.commande.config.CommandeProperties;
import com.okit.commande.domain.exception.EtatInvalideException;
import com.okit.commande.domain.exception.NotFoundException;
import com.okit.commande.domain.exception.RequeteInvalideException;
import com.okit.commande.persistence.entity.Commande;
import com.okit.commande.persistence.entity.Flocage;
import com.okit.commande.persistence.entity.LigneCommande;
import com.okit.commande.persistence.entity.StatutCommande;
import com.okit.commande.persistence.repository.CommandeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Panier = commande au statut PANIER. Gère aussi le flocage des maillots. */
@Service
@RequiredArgsConstructor
public class PanierService {

    static final int QUANTITE_MAX_PAR_TAILLE = 10;

    private final CommandeRepository commandeRepository;
    private final CatalogueClient catalogueClient;
    private final StockClient stockClient;
    private final CommandeProperties props;

    @Transactional
    public CommandeDto consulter(String idUtilisateur) {
        return CommandeDto.from(panierDe(idUtilisateur));
    }

    @Transactional
    public CommandeDto ajouterLigne(String idUtilisateur, AjoutLigne demande) {
        // 1. Le produit existe-t-il, est-il actif, la taille lui appartient-elle ?
        ProduitCatalogue produit = catalogueClient.getProduit(demande.idProduit());
        if (!produit.estActif()) {
            throw new RequeteInvalideException("Ce produit n'est plus disponible");
        }
        ProduitCatalogue.Variante variante = produit.variante(demande.idVariante())
                .orElseThrow(() -> new RequeteInvalideException("Cette taille n'existe pas pour ce produit"));

        // 2. Flocage demandé : le produit doit être flocable
        Flocage flocage = construireFlocage(produit, demande.flocage());

        // 3. Quantité totale de cette taille dans le panier : limite + stock disponible
        Commande panier = panierDe(idUtilisateur);
        int dejaDansPanier = quantiteDeLaVariante(panier, variante.id());
        verifierQuantite(variante.id(), dejaDansPanier + demande.quantite());

        // 4. Sans flocage : on fusionne avec une ligne identique. Avec flocage : toujours une nouvelle ligne.
        Optional<LigneCommande> identique = flocage != null ? Optional.empty()
                : panier.getLignes().stream()
                .filter(l -> l.getIdVariante().equals(variante.id()) && l.getFlocage() == null)
                .findFirst();

        if (identique.isPresent()) {
            LigneCommande ligne = identique.get();
            ligne.setQuantite(ligne.getQuantite() + demande.quantite());
            panier.recalculerTotal();
        } else {
            LigneCommande ligne = new LigneCommande();
            ligne.setIdProduit(produit.id());
            ligne.setIdVariante(variante.id());
            ligne.setTitreProduit(produit.titre());
            ligne.setTaille(variante.taille());
            ligne.setQuantite(demande.quantite());
            ligne.setPrixUnitaireHt(produit.prixUnitaireHt());   // prix figé au moment de l'ajout
            ligne.setTauxTva(produit.tauxTva());
            ligne.definirFlocage(flocage);
            panier.ajouterLigne(ligne);
        }
        return CommandeDto.from(commandeRepository.save(panier));
    }

    @Transactional
    public CommandeDto modifierQuantite(String idUtilisateur, Long idLigne, ModifierQuantite demande) {
        Commande panier = panierDe(idUtilisateur);
        LigneCommande ligne = ligneDuPanier(panier, idLigne);
        int autresLignes = quantiteDeLaVariante(panier, ligne.getIdVariante()) - ligne.getQuantite();
        verifierQuantite(ligne.getIdVariante(), autresLignes + demande.quantite());
        ligne.setQuantite(demande.quantite());
        panier.recalculerTotal();
        return CommandeDto.from(panier);
    }

    @Transactional
    public CommandeDto supprimerLigne(String idUtilisateur, Long idLigne) {
        Commande panier = panierDe(idUtilisateur);
        panier.retirerLigne(ligneDuPanier(panier, idLigne));
        return CommandeDto.from(panier);
    }

    @Transactional
    public CommandeDto vider(String idUtilisateur) {
        Commande panier = panierDe(idUtilisateur);
        panier.getLignes().clear();
        panier.recalculerTotal();
        return CommandeDto.from(panier);
    }

    // ------------------------------------------------------------------ privé

    private Commande panierDe(String idUtilisateur) {
        return commandeRepository.findByIdUtilisateurAndStatut(idUtilisateur, StatutCommande.PANIER)
                .orElseGet(() -> commandeRepository.save(
                        Commande.nouveauPanier(idUtilisateur, props.fraisLivraison())));
    }

    private static LigneCommande ligneDuPanier(Commande panier, Long idLigne) {
        return panier.getLignes().stream()
                .filter(l -> l.getId().equals(idLigne))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Ligne " + idLigne + " absente du panier"));
    }

    private static int quantiteDeLaVariante(Commande panier, Long idVariante) {
        return panier.getLignes().stream()
                .filter(l -> l.getIdVariante().equals(idVariante))
                .mapToInt(LigneCommande::getQuantite)
                .sum();
    }

    private void verifierQuantite(Long idVariante, int quantiteTotale) {
        if (quantiteTotale > QUANTITE_MAX_PAR_TAILLE) {
            throw new RequeteInvalideException(
                    "Maximum " + QUANTITE_MAX_PAR_TAILLE + " exemplaires par taille dans le panier");
        }
        if (!stockClient.estDisponible(idVariante, quantiteTotale)) {
            throw new EtatInvalideException("Stock insuffisant pour cette taille");
        }
    }

    private Flocage construireFlocage(ProduitCatalogue produit, FlocageDemande demande) {
        if (demande == null) {
            return null;
        }
        if (!produit.flocable()) {
            throw new RequeteInvalideException("Ce produit ne peut pas être floqué");
        }
        String texte = demande.texte().trim().toUpperCase();
        if (texte.length() > props.flocageTexteMax()) {
            throw new RequeteInvalideException(
                    "Le nom floqué ne doit pas dépasser " + props.flocageTexteMax() + " caractères");
        }
        return new Flocage(texte, demande.numero(), props.prixFlocageHt());
    }
}
