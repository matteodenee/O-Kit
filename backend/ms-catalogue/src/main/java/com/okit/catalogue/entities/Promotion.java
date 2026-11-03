package com.okit.catalogue.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "promotion")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_promo")
    private Long idPromo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_produit", nullable = false)
    private Produit produit;

    @Column(name = "code")
    private String code;

    @NotNull(message = "Le pourcentage de réduction est obligatoire")
    @Column(name = "pourcentage_reduction", nullable = false, precision = 5, scale = 2)
    private BigDecimal pourcentageReduction;

    @NotNull(message = "La date de début est obligatoire")
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull(message = "La date de fin est obligatoire")
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    public Promotion() {
    }

    public Promotion(Produit produit, String code, BigDecimal pourcentageReduction, LocalDate dateDebut, LocalDate dateFin) {
        this.produit = produit;
        this.code = code;
        this.pourcentageReduction = pourcentageReduction;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    public Promotion(Long idPromo, Produit produit, String code, BigDecimal pourcentageReduction, LocalDate dateDebut, LocalDate dateFin) {
        this.idPromo = idPromo;
        this.produit = produit;
        this.code = code;
        this.pourcentageReduction = pourcentageReduction;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    public Long getIdPromo() {
        return idPromo;
    }

    public void setIdPromo(Long idPromo) {
        this.idPromo = idPromo;
    }

    public Produit getProduit() {
        return produit;
    }

    public void setProduit(Produit produit) {
        this.produit = produit;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getPourcentageReduction() {
        return pourcentageReduction;
    }

    public void setPourcentageReduction(BigDecimal pourcentageReduction) {
        this.pourcentageReduction = pourcentageReduction;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Promotion promotion = (Promotion) o;
        return idPromo != null && Objects.equals(idPromo, promotion.idPromo);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Promotion{" +
                "idPromo=" + idPromo +
                ", code='" + code + '\'' +
                ", pourcentageReduction=" + pourcentageReduction +
                ", dateDebut=" + dateDebut +
                ", dateFin=" + dateFin +
                '}';
    }
}
