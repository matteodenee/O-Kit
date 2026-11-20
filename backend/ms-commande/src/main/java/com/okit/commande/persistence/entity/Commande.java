package com.okit.commande.persistence.entity;

import com.okit.commande.domain.exception.EtatInvalideException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Une commande. Le PANIER est simplement une commande au statut PANIER
 * (au plus un par utilisateur, garanti par un index unique en base).
 */
@Entity
@Table(name = "commandes")
@Getter
@Setter
@NoArgsConstructor
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_commande")
    private Long id;

    /** "sub" du JWT Keycloak (pas de clé étrangère vers ms-utilisateur). */
    @Column(nullable = false)
    private String idUtilisateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCommande statut;

    private Instant dateCommande;

    @Column(nullable = false)
    private BigDecimal prixHt = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal fraisLivraison = BigDecimal.ZERO;

    private String codeSuiviTransporteur;

    private String idTransaction;

    /** "orderId" envoyé à ms-stock : sert de clé d'idempotence pour decrement / release. */
    private String referenceStock;

    @Embedded
    private AdresseLivraison adresseLivraison;

    @CreationTimestamp
    private Instant dateCreation;

    @UpdateTimestamp
    private Instant dateModification;

    @Version
    private Long version;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneCommande> lignes = new ArrayList<>();

    public static Commande nouveauPanier(String idUtilisateur, BigDecimal fraisLivraison) {
        Commande panier = new Commande();
        panier.idUtilisateur = idUtilisateur;
        panier.statut = StatutCommande.PANIER;
        panier.fraisLivraison = fraisLivraison;
        return panier;
    }

    public void ajouterLigne(LigneCommande ligne) {
        ligne.setCommande(this);
        lignes.add(ligne);
        recalculerTotal();
    }

    public void retirerLigne(LigneCommande ligne) {
        lignes.remove(ligne);
        recalculerTotal();
    }

    public List<LigneCommande> lignesActives() {
        return lignes.stream().filter(LigneCommande::estActive).toList();
    }

    /** prix_ht = somme des lignes actives (les lignes annulées ne comptent plus). */
    public void recalculerTotal() {
        prixHt = lignesActives().stream()
                .map(LigneCommande::totalHt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total TTC des articles + frais de livraison (forfait TTC, offert si panier vide). */
    public BigDecimal totalTtc() {
        BigDecimal articles = lignesActives().stream()
                .map(LigneCommande::totalTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return lignesActives().isEmpty() ? articles : articles.add(fraisLivraison);
    }

    public void changerStatut(StatutCommande cible) {
        if (!statut.peutPasserA(cible)) {
            throw new EtatInvalideException(
                    "Transition interdite : " + statut + " -> " + cible + " (commande " + id + ")");
        }
        this.statut = cible;
    }
}
