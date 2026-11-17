package com.okit.commande.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Flocage_Personnalise : nom + numéro imprimés sur le maillot d'une ligne de commande. */
@Entity
@Table(name = "flocages")
@Getter
@Setter
@NoArgsConstructor
public class Flocage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_flocage")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ligne_commande", nullable = false, unique = true)
    private LigneCommande ligne;

    @Column(nullable = false)
    private String texte;

    @Column(nullable = false)
    private Integer numero;

    /** Prix HT d'UN flocage, figé au moment de l'ajout au panier. */
    @Column(nullable = false)
    private BigDecimal prix;

    public Flocage(String texte, Integer numero, BigDecimal prix) {
        this.texte = texte;
        this.numero = numero;
        this.prix = prix;
    }
}
