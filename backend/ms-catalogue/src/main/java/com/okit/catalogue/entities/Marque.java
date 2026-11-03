package com.okit.catalogue.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

@Entity
@Table(name = "marque")
public class Marque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marque")
    private Long idMarque;

    @NotBlank(message = "Le nom de la marque est obligatoire")
    @Column(name = "nom_marque", nullable = false)
    private String nomMarque;

    @Column(name = "logo_url")
    private String logoUrl;

    public Marque() {
    }

    public Marque(String nomMarque) {
        this.nomMarque = nomMarque;
    }

    public Marque(String nomMarque, String logoUrl) {
        this.nomMarque = nomMarque;
        this.logoUrl = logoUrl;
    }

    public Marque(Long idMarque, String nomMarque, String logoUrl) {
        this.idMarque = idMarque;
        this.nomMarque = nomMarque;
        this.logoUrl = logoUrl;
    }

    public Long getIdMarque() {
        return idMarque;
    }

    public void setIdMarque(Long idMarque) {
        this.idMarque = idMarque;
    }

    public String getNomMarque() {
        return nomMarque;
    }

    public void setNomMarque(String nomMarque) {
        this.nomMarque = nomMarque;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Marque marque = (Marque) o;
        return idMarque != null && Objects.equals(idMarque, marque.idMarque);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Marque{" +
                "idMarque=" + idMarque +
                ", nomMarque='" + nomMarque + '\'' +
                ", logoUrl='" + logoUrl + '\'' +
                '}';
    }
}
