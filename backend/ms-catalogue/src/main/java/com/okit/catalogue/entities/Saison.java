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
@Table(name = "saison")
public class Saison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_saison")
    private Long idSaison;

    @NotBlank(message = "Le nom de la saison est obligatoire")
    @Column(name = "nom", nullable = false)
    private String nom;

    public Saison() {
    }

    public Saison(String nom) {
        this.nom = nom;
    }

    public Saison(Long idSaison, String nom) {
        this.idSaison = idSaison;
        this.nom = nom;
    }

    public Long getIdSaison() {
        return idSaison;
    }

    public void setIdSaison(Long idSaison) {
        this.idSaison = idSaison;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Saison saison = (Saison) o;
        return idSaison != null && Objects.equals(idSaison, saison.idSaison);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Saison{" +
                "idSaison=" + idSaison +
                ", nom='" + nom + '\'' +
                '}';
    }
}
