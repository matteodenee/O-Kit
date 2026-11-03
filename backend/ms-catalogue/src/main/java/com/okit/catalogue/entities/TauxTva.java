package com.okit.catalogue.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "taux_tva")
public class TauxTva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_taux_tva")
    private Long idTauxTVA;

    @NotNull(message = "Le taux de TVA est obligatoire")
    @Column(name = "taux", nullable = false, precision = 5, scale = 2)
    private BigDecimal taux;

    public TauxTva() {
    }

    public TauxTva(BigDecimal taux) {
        this.taux = taux;
    }

    public TauxTva(Long idTauxTVA, BigDecimal taux) {
        this.idTauxTVA = idTauxTVA;
        this.taux = taux;
    }

    public Long getIdTauxTVA() {
        return idTauxTVA;
    }

    public void setIdTauxTVA(Long idTauxTVA) {
        this.idTauxTVA = idTauxTVA;
    }

    public Long getIdTauxTva() {
        return idTauxTVA;
    }

    public void setIdTauxTva(Long idTauxTva) {
        this.idTauxTVA = idTauxTva;
    }

    public BigDecimal getTaux() {
        return taux;
    }

    public void setTaux(BigDecimal taux) {
        this.taux = taux;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TauxTva tauxTva = (TauxTva) o;
        return idTauxTVA != null && Objects.equals(idTauxTVA, tauxTva.idTauxTVA);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "TauxTva{" +
                "idTauxTVA=" + idTauxTVA +
                ", taux=" + taux +
                '}';
    }
}
