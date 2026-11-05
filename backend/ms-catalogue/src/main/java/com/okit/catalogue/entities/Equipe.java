package com.okit.catalogue.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@Entity
@Table(name = "equipe")
public class Equipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_equipe")
    private Long idEquipe;

    @NotBlank(message = "Le nom de l'équipe est obligatoire")
    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "logo_url")
    private String logoUrl;

    @NotNull(message = "Le type d'équipe est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private EquipeType type;

    public Equipe() {
    }

    public Equipe(String nom, EquipeType type) {
        this.nom = nom;
        this.type = type;
    }

    public Equipe(String nom, String logoUrl, EquipeType type) {
        this.nom = nom;
        this.logoUrl = logoUrl;
        this.type = type;
    }

    public Equipe(Long idEquipe, String nom, String logoUrl, EquipeType type) {
        this.idEquipe = idEquipe;
        this.nom = nom;
        this.logoUrl = logoUrl;
        this.type = type;
    }

    public Long getIdEquipe() {
        return idEquipe;
    }

    public void setIdEquipe(Long idEquipe) {
        this.idEquipe = idEquipe;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public EquipeType getType() {
        return type;
    }

    public void setType(EquipeType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Equipe equipe = (Equipe) o;
        return idEquipe != null && Objects.equals(idEquipe, equipe.idEquipe);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Equipe{" +
                "idEquipe=" + idEquipe +
                ", nom='" + nom + '\'' +
                ", logoUrl='" + logoUrl + '\'' +
                ", type=" + type +
                '}';
    }
}
