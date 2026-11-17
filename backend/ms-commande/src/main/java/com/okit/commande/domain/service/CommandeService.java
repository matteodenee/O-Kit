package com.okit.commande.domain.service;

import com.okit.commande.api.dto.CommandeDto;
import com.okit.commande.api.dto.Requetes.Adresse;
import com.okit.commande.api.dto.Requetes.ChangerStatut;
import com.okit.commande.api.dto.Requetes.ModifierCommande;
import com.okit.commande.api.dto.Requetes.ValiderCommande;
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
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandeService {

    private final CommandeRepository commandeRepository;
    private final StockClient stockClient;
    private final PaiementClient paiementClient;
    private final TransactionTemplate transaction;
    private final CommandeProperties props;

    /** Ce qu'on retient du panier entre les étapes de la validation. */
    private record Preparation(Long idCommande, Long version, String reference,
                               BigDecimal totalTtc, List<LigneStock> lignes) {
    }

    // ================================================================= ACHETEUR

    /**
     * Valide le panier : PANIER -> CONFIRMEE.
     * <ol>
     *   <li>enregistre l'adresse et une référence unique pour ms-stock (transaction courte)</li>
     *   <li>paiement (appel bloquant à ms-paiement)</li>
     *   <li>décrément du stock (tout ou rien, idempotent) ; si échec -> remboursement</li>
     *   <li>passe la commande à CONFIRMEE</li>
     * </ol>
     * Pas de grosse transaction autour des appels HTTP : on ne bloque pas la base pendant les appels réseau.
     */
    public CommandeDto valider(String idUtilisateur, ValiderCommande demande) {
        // 1. Préparer
        Preparation prep = transaction.execute(status -> {
            Commande panier = commandeRepository.findByIdUtilisateurAndStatut(idUtilisateur, StatutCommande.PANIER)
                    .filter(p -> !p.lignesActives().isEmpty())
                    .orElseThrow(() -> new EtatInvalideException("Votre panier est vide"));
            panier.setAdresseLivraison(versEntite(demande.adresseLivraison()));
            panier.setFraisLivraison(props.fraisLivraison());
            panier.recalculerTotal();
            // Nouvelle référence à chaque tentative : un essai raté ne bloque jamais le suivant
            panier.setReferenceStock("CMD-" + panier.getId() + "-" + System.currentTimeMillis());
            panier = commandeRepository.saveAndFlush(panier);
            return new Preparation(panier.getId(), panier.getVersion(), panier.getReferenceStock(),
                    panier.totalTtc(), versLignesStock(panier.lignesActives()));
        });
        Objects.requireNonNull(prep);

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
        Commande commande = commandeDe(idUtilisateur, idCommande);
        annulerCommande(commande);
        return CommandeDto.from(commande);
    }

    /** Annule une ligne : stock ré-incrémenté, remboursement de la ligne, montant total recalculé. */
    @Transactional
    public CommandeDto annulerLigne(String idUtilisateur, Long idCommande, Long idLigne) {
        Commande commande = commandeDe(idUtilisateur, idCommande);
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
        Commande commande = commandeNonPanier(idCommande);
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

    private Commande commandeNonPanier(Long idCommande) {
        return commandeRepository.findById(idCommande)
                .filter(c -> c.getStatut() != StatutCommande.PANIER)
                .orElseThrow(() -> new NotFoundException("Commande " + idCommande + " introuvable"));
    }

    private static List<LigneStock> versLignesStock(List<LigneCommande> lignes) {
        return lignes.stream().map(l -> new LigneStock(l.getIdVariante(), l.getQuantite())).toList();
    }

    private static AdresseLivraison versEntite(Adresse a) {
        return new AdresseLivraison(a.rue(), a.complement(), a.codePostal(), a.ville());
    }
}
