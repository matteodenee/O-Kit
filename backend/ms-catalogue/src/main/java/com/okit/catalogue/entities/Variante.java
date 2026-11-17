package com.okit.catalogue.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "variante",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_produit", "taille"}))
public class Variante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_variante")
    private Long idVariante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_produit", nullable = false)
    private Produit produit;

    @Enumerated(EnumType.STRING)
    @Column(name = "taille", nullable = false, length = 5)
    private Taille taille;

    public Variante() {}

    public Variante(Produit produit, Taille taille) {
        this.produit = produit;
        this.taille = taille;
    }

    public Long getIdVariante() { return idVariante; }
    public Produit getProduit() { return produit; }
    public void setProduit(Produit produit) { this.produit = produit; }
    public Taille getTaille() { return taille; }
    public void setTaille(Taille taille) { this.taille = taille; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Variante v = (Variante) o;
        return idVariante != null && idVariante.equals(v.idVariante);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}