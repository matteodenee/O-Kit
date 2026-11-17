package com.okit.commande.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Copie de l'adresse au moment de la commande : si l'utilisateur la modifie ensuite, la commande ne change pas. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdresseLivraison {

    @Column(name = "livraison_rue")
    private String rue;

    @Column(name = "livraison_complement")
    private String complement;

    @Column(name = "livraison_code_postal")
    private String codePostal;

    @Column(name = "livraison_ville")
    private String ville;
}
