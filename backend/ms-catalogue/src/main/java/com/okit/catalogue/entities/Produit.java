package com.okit.catalogue.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "produit")
public class Produit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_produit")
    private Long idProduit;

    @NotBlank(message = "Le titre du produit est obligatoire")
    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "description", length = 2000, columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Le prix de base est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix de base doit être strictement supérieur à 0")
    @Column(name = "prix_de_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixDeBase;

    @Column(name = "poids")
    private Double poids;

    @Column(name = "couleurs_principales")
    private String couleursPrincipales;

    @Column(name = "impact_envi")
    private String impactEnvi;

    @Column(name = "est_flocable", nullable = false)
    private Boolean estFlocable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private ProduitStatut statut = ProduitStatut.ACTIF;

    @Column(name = "en_stock", nullable = false)
    private Boolean enStock = true;

    @NotNull(message = "La saison est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_saison", nullable = false)
    private Saison saison;

    @NotNull(message = "La marque est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_marque", nullable = false)
    private Marque marque;

    @NotNull(message = "Le taux de TVA est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_taux_tva", nullable = false)
    private TauxTva tauxTva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipe", nullable = true)
    private Equipe equipe;

    @OneToMany(mappedBy = "produit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Promotion> promotions = new ArrayList<>();

    public Produit() {
    }

    public Produit(String titre, String description, BigDecimal prixDeBase, Saison saison, Marque marque, TauxTva tauxTva) {
        this.titre = titre;
        this.description = description;
        this.prixDeBase = prixDeBase;
        this.saison = saison;
        this.marque = marque;
        this.tauxTva = tauxTva;
    }

    public Long getIdProduit() {
        return idProduit;
    }

    public void setIdProduit(Long idProduit) {
        this.idProduit = idProduit;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrixDeBase() {
        return prixDeBase;
    }

    public void setPrixDeBase(BigDecimal prixDeBase) {
        this.prixDeBase = prixDeBase;
    }

    public Double getPoids() {
        return poids;
    }

    public void setPoids(Double poids) {
        this.poids = poids;
    }

    public String getCouleursPrincipales() {
        return couleursPrincipales;
    }

    public void setCouleursPrincipales(String couleursPrincipales) {
        this.couleursPrincipales = couleursPrincipales;
    }

    public String getImpactEnvi() {
        return impactEnvi;
    }

    public void setImpactEnvi(String impactEnvi) {
        this.impactEnvi = impactEnvi;
    }

    public Boolean getEstFlocable() {
        return estFlocable;
    }

    public boolean isEstFlocable() {
        return Boolean.TRUE.equals(estFlocable);
    }

    public void setEstFlocable(Boolean estFlocable) {
        this.estFlocable = estFlocable;
    }

    public ProduitStatut getStatut() {
        return statut;
    }

    public void setStatut(ProduitStatut statut) {
        this.statut = statut;
    }

    public Boolean getEnStock() {
        return enStock;
    }

    public boolean isEnStock() {
        return Boolean.TRUE.equals(enStock);
    }

    public void setEnStock(Boolean enStock) {
        this.enStock = enStock;
    }

    public Saison getSaison() {
        return saison;
    }

    public void setSaison(Saison saison) {
        this.saison = saison;
    }

    public Marque getMarque() {
        return marque;
    }

    public void setMarque(Marque marque) {
        this.marque = marque;
    }

    public TauxTva getTauxTva() {
        return tauxTva;
    }

    public void setTauxTva(TauxTva tauxTva) {
        this.tauxTva = tauxTva;
    }

    public Equipe getEquipe() {
        return equipe;
    }

    public void setEquipe(Equipe equipe) {
        this.equipe = equipe;
    }

    public List<Promotion> getPromotions() {
        return promotions;
    }

    public void setPromotions(List<Promotion> promotions) {
        this.promotions = promotions;
    }

    public void addPromotion(Promotion promotion) {
        if (promotion != null) {
            promotions.add(promotion);
            promotion.setProduit(this);
        }
    }

    public void removePromotion(Promotion promotion) {
        if (promotion != null) {
            promotions.remove(promotion);
            promotion.setProduit(null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Produit produit = (Produit) o;
        return idProduit != null && Objects.equals(idProduit, produit.idProduit);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Produit{" +
                "idProduit=" + idProduit +
                ", titre='" + titre + '\'' +
                ", prixDeBase=" + prixDeBase +
                ", poids=" + poids +
                ", couleursPrincipales='" + couleursPrincipales + '\'' +
                ", impactEnvi='" + impactEnvi + '\'' +
                ", estFlocable=" + estFlocable +
                ", statut=" + statut +
                ", enStock=" + enStock +
                '}';
    }
}
