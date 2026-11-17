package com.okit.commande.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "lignes_commande")
@Getter
@Setter
@NoArgsConstructor
public class LigneCommande {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ligne_commande")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_commande", nullable = false)
    private Commande commande;

    @Column(nullable = false)
    private Long idProduit;

    @Column(nullable = false)
    private Long idVariante;

    @Column(nullable = false)
    private String titreProduit;

    private String taille;

    @Column(nullable = false)
    private Integer quantite;

    /** Prix stocké au moment de l'achat : le prix catalogue peut changer plus tard. */
    @Column(nullable = false)
    private BigDecimal prixUnitaireHt;

    /** En pourcentage (20.00 = 20 %), même convention que ms-catalogue. */
    @Column(nullable = false)
    private BigDecimal tauxTva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutLigne statut = StatutLigne.ACTIVE;

    @OneToOne(mappedBy = "ligne", cascade = CascadeType.ALL, orphanRemoval = true)
    private Flocage flocage;

    public void definirFlocage(Flocage nouveau) {
        if (nouveau != null) {
            nouveau.setLigne(this);
        }
        this.flocage = nouveau;
    }

    public boolean estActive() {
        return statut == StatutLigne.ACTIVE;
    }

    /** (prix unitaire + flocage éventuel) x quantité : chaque maillot de la ligne est floqué. */
    public BigDecimal totalHt() {
        BigDecimal unitaire = flocage == null ? prixUnitaireHt : prixUnitaireHt.add(flocage.getPrix());
        return unitaire.multiply(BigDecimal.valueOf(quantite)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal totalTtc() {
        BigDecimal multiplicateur = BigDecimal.ONE.add(tauxTva.divide(CENT, 4, RoundingMode.HALF_UP));
        return totalHt().multiply(multiplicateur).setScale(2, RoundingMode.HALF_UP);
    }
}
