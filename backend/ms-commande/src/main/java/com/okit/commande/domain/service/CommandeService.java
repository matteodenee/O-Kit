package com.okit.commande.domain.service;

import com.okit.commande.api.dto.CommandeDto;
import com.okit.commande.api.dto.Requetes.Adresse;
import com.okit.commande.api.dto.Requetes.ChangerStatut;
import com.okit.commande.api.dto.Requetes.ModifierCommande;
import com.okit.commande.api.dto.Requetes.ValiderCommande;
import com.okit.commande.client.CatalogueClient;
import com.okit.commande.client.CatalogueClient.ProduitCatalogue;
import com.okit.commande.client.PaiementClient;
import com.okit.commande.client.PaiementClient.ResultatPaiement;
import com.okit.commande.client.StockClient;
import com.okit.commande.client.StockClient.LigneStock;
import com.okit.commande.config.CommandeProperties;
import com.okit.commande.domain.exception.EtatInvalideException;
import com.okit.commande.domain.exception.NotFoundException;
import com.okit.commande.domain.exception.PaiementRefuseException;
import com.okit.commande.domain.exception.RequeteInvalideException;
import com.okit.commande.domain.exception.StockInsuffisantException;
import com.okit.commande.persistence.entity.AdresseLivraison;
import com.okit.commande.persistence.entity.Commande;
import com.okit.commande.persistence.entity.LigneCommande;
import com.okit.commande.persistence.entity.StatutCommande;
import com.okit.commande.persistence.entity.StatutLigne;
import com.okit.commande.persistence.repository.CommandeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandeService {

    private final CommandeRepository commandeRepository;
    private final CatalogueClient catalogueClient;
    private final StockClient stockClient;
    private final PaiementClient paiementClient;
    private final TransactionTemplate transaction;
    private final CommandeProperties props;

    /** Ce qu'on retient du panier entre les étapes de la validation. */
    private record Preparation(Long idCommande, Long version, String reference,
                               BigDecimal totalTtc, List<LigneStock> lignes, boolean prixModifies) {

        static Preparation aRevoir() {
            return new Preparation(null, null, null, null, List.of(), true);
        }
    }

    // ================================================================= ACHETEUR

    /**
     * Valide le panier : PANIER -> CONFIRMEE.
     * <ol>
     *   <li>relit le catalogue : produit encore vendu ? prix et TVA à jour ? (le prix est celui de l'achat)</li>
     *   <li>enregistre l'adresse et une référence unique pour ms-stock (transaction courte)</li>
     *   <li>paiement (appel bloquant à ms-paiement)</li>
     *   <li>décrément du stock (tout ou rien, idempotent) ; si échec -> remboursement</li>
     *   <li>passe la commande à CONFIRMEE</li>
     * </ol>
     * Pas de grosse transaction autour des appels HTTP : on ne bloque pas la base pendant les appels réseau.
     */
    public CommandeDto valider(String idUtilisateur, ValiderCommande demande) {
        // 0. Relire le catalogue (hors transaction : appels réseau)
        Map<Long, Optional<ProduitCatalogue>> catalogue = lireCatalogue(idUtilisateur);

        // 1. Préparer
        Preparation prep = transaction.execute(status -> {
            Commande panier = commandeRepository.findByIdUtilisateurAndStatut(idUtilisateur, StatutCommande.PANIER)
                    .filter(p -> !p.lignesActives().isEmpty())
                    .orElseThrow(() -> new EtatInvalideException("Votre panier est vide"));
            if (actualiserDepuisCatalogue(panier, catalogue)) {
                // Nouveaux prix enregistrés (commit) : l'acheteur doit revoir son total avant de payer
                return Preparation.aRevoir();
            }
            panier.setAdresseLivraison(versEntite(demande.adresseLivraison()));
            panier.setFraisLivraison(props.fraisLivraison());
            panier.recalculerTotal();
            // Nouvelle référence à chaque tentative : un essai raté ne bloque jamais le suivant
            panier.setReferenceStock("CMD-" + panier.getId() + "-" + System.currentTimeMillis());
            panier = commandeRepository.saveAndFlush(panier);
            return new Preparation(panier.getId(), panier.getVersion(), panier.getReferenceStock(),
                    panier.totalTtc(), versLignesStock(panier.lignesActives()), false);
        });
        Objects.requireNonNull(prep);
        if (prep.prixModifies()) {
            throw new EtatInvalideException(
                    "Le prix d'au moins un article a changé : vérifiez le nouveau total de votre panier puis validez à nouveau");
        }

        // 2. Payer
        ResultatPaiement paiement = paiementClient.payer(prep.idCommande(), idUtilisateur, prep.totalTtc());
        if (!paiement.accepte()) {
            throw new PaiementRefuseException("Paiement refusé");
        }

        // 3. Décrémenter le stock (ORDER_PAID)
        try {
            stockClient.decrementer(prep.reference(), prep.lignes());
        } catch (StockInsuffisantException e) {
            // Rien n'a été décrémenté (tout ou rien) : on rembourse simplement
            paiementClient.rembourser(paiement.idTransaction(), prep.totalTtc());
            throw e;
        } catch (RuntimeException e) {
            log.error("Décrément incertain pour {} (stock injoignable) : remboursement, à vérifier manuellement",
                    prep.reference(), e);
            paiementClient.rembourser(paiement.idTransaction(), prep.totalTtc());
            throw e;
        }

        // 4. Confirmer
        return transaction.execute(status -> {
            Commande commande = commandeRepository.findById(prep.idCommande()).orElseThrow();
            if (!Objects.equals(commande.getVersion(), prep.version())) {
                // Le panier a été modifié pendant le paiement : on défait tout
                stockClient.liberer(prep.reference(), prep.lignes());
                paiementClient.rembourser(paiement.idTransaction(), prep.totalTtc());
                throw new EtatInvalideException("Le panier a été modifié pendant la validation, réessayez");
            }
            commande.changerStatut(StatutCommande.CONFIRMEE);
            commande.setDateCommande(Instant.now());
            commande.setIdTransaction(paiement.idTransaction());
            return CommandeDto.from(commande);
        });
    }

    @Transactional(readOnly = true)
    public List<CommandeDto> historique(String idUtilisateur) {
        return commandeRepository
                .findByIdUtilisateurAndStatutNotOrderByDateCommandeDesc(idUtilisateur, StatutCommande.PANIER)
                .stream().map(CommandeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public CommandeDto detail(String idUtilisateur, Long idCommande) {
        return CommandeDto.from(commandeDe(idUtilisateur, idCommande));
    }

    /** Modification côté client : l'adresse de livraison, tant que la commande n'est pas expédiée. */
    @Transactional
    public CommandeDto modifier(String idUtilisateur, Long idCommande, ModifierCommande demande) {
        Commande commande = commandeDe(idUtilisateur, idCommande);
        if (!commande.getStatut().estAnnulable()) {
            throw new EtatInvalideException("La commande est déjà expédiée, l'adresse ne peut plus changer");
        }
        commande.setAdresseLivraison(versEntite(demande.adresseLivraison()));
        return CommandeDto.from(commande);
    }

    @Transactional
    public CommandeDto annuler(String idUtilisateur, Long idCommande) {
        Commande commande = verrouiller(idCommande, idUtilisateur);
        annulerCommande(commande);
        return CommandeDto.from(commande);
    }

    /** Annule une ligne : stock ré-incrémenté, remboursement de la ligne, montant total recalculé. */
    @Transactional
    public CommandeDto annulerLigne(String idUtilisateur, Long idCommande, Long idLigne) {
        Commande commande = verrouiller(idCommande, idUtilisateur);
        if (!commande.getStatut().estAnnulable()) {
            throw new EtatInvalideException("La commande est déjà expédiée, elle ne peut plus être modifiée");
        }
        LigneCommande ligne = commande.getLignes().stream()
                .filter(l -> l.getId().equals(idLigne))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Ligne " + idLigne + " introuvable"));
        if (!ligne.estActive()) {
            throw new EtatInvalideException("Cette ligne est déjà annulée");
        }
        if (commande.lignesActives().size() == 1) {
            annulerCommande(commande);  // dernière ligne -> toute la commande
            return CommandeDto.from(commande);
        }
        // Référence distincte par ligne : ms-stock est idempotent par référence
        stockClient.liberer(commande.getReferenceStock() + "-L" + ligne.getId(), versLignesStock(List.of(ligne)));
        paiementClient.rembourser(commande.getIdTransaction(), ligne.totalTtc());
        ligne.setStatut(StatutLigne.ANNULEE);
        commande.recalculerTotal();
        return CommandeDto.from(commande);
    }

    // ================================================================= VENDEUR

    @Transactional(readOnly = true)
    public Page<CommandeDto> listerPourVendeur(StatutCommande statut, Pageable pageable) {
        Page<Commande> page = statut == null
                ? commandeRepository.findByStatutNot(StatutCommande.PANIER, pageable)
                : commandeRepository.findByStatut(statut, pageable);
        return page.map(CommandeDto::from);
    }

    @Transactional(readOnly = true)
    public CommandeDto detailPourVendeur(Long idCommande) {
        return CommandeDto.from(commandeNonPanier(idCommande));
    }

    @Transactional
    public CommandeDto changerStatut(Long idCommande, ChangerStatut demande) {
        Commande commande = verrouiller(idCommande, null);
        if (demande.statut() == StatutCommande.ANNULEE) {
            annulerCommande(commande);
            return CommandeDto.from(commande);
        }
        if (demande.statut() == StatutCommande.EXPEDIEE && !StringUtils.hasText(demande.codeSuiviTransporteur())) {
            throw new RequeteInvalideException("Le code de suivi transporteur est obligatoire pour expédier");
        }
        commande.changerStatut(demande.statut());
        if (StringUtils.hasText(demande.codeSuiviTransporteur())) {
            commande.setCodeSuiviTransporteur(demande.codeSuiviTransporteur());
        }
        return CommandeDto.from(commande);
    }

    // ================================================================= privé

    private void annulerCommande(Commande commande) {
        if (!commande.getStatut().estAnnulable()) {
            throw new EtatInvalideException("Une commande " + commande.getStatut() + " ne peut plus être annulée");
        }
        BigDecimal montant = commande.totalTtc();
        stockClient.liberer(commande.getReferenceStock(), versLignesStock(commande.lignesActives()));
        paiementClient.rembourser(commande.getIdTransaction(), montant);
        commande.changerStatut(StatutCommande.ANNULEE);
    }

    /** Une commande de l'utilisateur (jamais celle d'un autre, jamais son panier). */
    private Commande commandeDe(String idUtilisateur, Long idCommande) {
        return commandeRepository.findByIdAndIdUtilisateur(idCommande, idUtilisateur)
                .filter(c -> c.getStatut() != StatutCommande.PANIER)
                .orElseThrow(() -> new NotFoundException("Commande " + idCommande + " introuvable"));
    }

    /**
     * Comme {@link #commandeDe} / {@link #commandeNonPanier}, mais verrouillée jusqu'à la fin de la transaction.
     * À utiliser avant toute opération qui libère du stock ou rembourse : ces appels externes ne sont pas
     * annulés par un rollback, il ne faut donc jamais les faire deux fois pour la même commande.
     *
     * @param idUtilisateur propriétaire attendu, ou null côté vendeur
     */
    private Commande verrouiller(Long idCommande, String idUtilisateur) {
        return commandeRepository.findByIdPourMiseAJour(idCommande)
                .filter(c -> c.getStatut() != StatutCommande.PANIER)
                .filter(c -> idUtilisateur == null || c.getIdUtilisateur().equals(idUtilisateur))
                .orElseThrow(() -> new NotFoundException("Commande " + idCommande + " introuvable"));
    }

    private Commande commandeNonPanier(Long idCommande) {
        return commandeRepository.findById(idCommande)
                .filter(c -> c.getStatut() != StatutCommande.PANIER)
                .orElseThrow(() -> new NotFoundException("Commande " + idCommande + " introuvable"));
    }

    /** Fiche catalogue de chaque produit du panier ; Optional.empty() si le produit a été supprimé. */
    private Map<Long, Optional<ProduitCatalogue>> lireCatalogue(String idUtilisateur) {
        Set<Long> idsProduits = transaction.execute(status ->
                commandeRepository.findByIdUtilisateurAndStatut(idUtilisateur, StatutCommande.PANIER)
                        .map(p -> p.lignesActives().stream().map(LigneCommande::getIdProduit)
                                .collect(Collectors.toSet()))
                        .orElse(Set.of()));
        Map<Long, Optional<ProduitCatalogue>> catalogue = new HashMap<>();
        for (Long idProduit : Objects.requireNonNull(idsProduits)) {
            try {
                catalogue.put(idProduit, Optional.of(catalogueClient.getProduit(idProduit)));
            } catch (NotFoundException e) {
                catalogue.put(idProduit, Optional.empty());
            }
        }
        return catalogue;
    }

    /**
     * Confronte chaque ligne du panier au catalogue actuel.
     *
     * @return true si au moins un prix (article, TVA ou flocage) a été mis à jour
     * @throws EtatInvalideException si un article n'est plus vendable (produit retiré, taille supprimée,
     *                               flocage plus proposé) : l'acheteur doit le retirer de son panier
     */
    private boolean actualiserDepuisCatalogue(Commande panier, Map<Long, Optional<ProduitCatalogue>> catalogue) {
        List<String> indisponibles = new ArrayList<>();
        boolean prixModifies = false;
        for (LigneCommande ligne : panier.lignesActives()) {
            if (!catalogue.containsKey(ligne.getIdProduit())) {
                // Article ajouté pendant la validation (autre onglet) : pas encore vérifié
                throw new EtatInvalideException("Le panier a été modifié pendant la validation, réessayez");
            }
            ProduitCatalogue produit = catalogue.get(ligne.getIdProduit()).orElse(null);
            boolean vendable = produit != null && produit.estActif()
                    && produit.variante(ligne.getIdVariante()).isPresent()
                    && (ligne.getFlocage() == null || produit.flocable());
            if (!vendable) {
                indisponibles.add(ligne.getTitreProduit() + " (" + ligne.getTaille() + ")");
                continue;
            }
            if (ligne.getPrixUnitaireHt().compareTo(produit.prixUnitaireHt()) != 0
                    || ligne.getTauxTva().compareTo(produit.tauxTva()) != 0) {
                ligne.setPrixUnitaireHt(produit.prixUnitaireHt());
                ligne.setTauxTva(produit.tauxTva());
                prixModifies = true;
            }
            if (ligne.getFlocage() != null && ligne.getFlocage().getPrix().compareTo(props.prixFlocageHt()) != 0) {
                ligne.getFlocage().setPrix(props.prixFlocageHt());
                prixModifies = true;
            }
        }
        if (!indisponibles.isEmpty()) {
            throw new EtatInvalideException("Ces articles ne sont plus disponibles, retirez-les de votre panier : "
                    + String.join(", ", indisponibles));
        }
        if (prixModifies) {
            panier.recalculerTotal();
        }
        return prixModifies;
    }

    private static List<LigneStock> versLignesStock(List<LigneCommande> lignes) {
        return lignes.stream().map(l -> new LigneStock(l.getIdVariante(), l.getQuantite())).toList();
    }

    private static AdresseLivraison versEntite(Adresse a) {
        return new AdresseLivraison(a.rue(), a.complement(), a.codePostal(), a.ville());
    }
}
