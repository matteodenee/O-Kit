package com.okit.commande.persistence.entity;

import java.util.Set;

/** Cycle de vie : PANIER -> CONFIRMEE -> EN_PREPARATION -> EXPEDIEE -> LIVREE (ou ANNULEE). */
public enum StatutCommande {
    PANIER, CONFIRMEE, EN_PREPARATION, EXPEDIEE, LIVREE, ANNULEE;

    public boolean peutPasserA(StatutCommande cible) {
        return switch (this) {
            case PANIER -> cible == CONFIRMEE;
            case CONFIRMEE -> Set.of(EN_PREPARATION, ANNULEE).contains(cible);
            case EN_PREPARATION -> Set.of(EXPEDIEE, ANNULEE).contains(cible);
            case EXPEDIEE -> cible == LIVREE;
            case LIVREE, ANNULEE -> false;
        };
    }

    /** Une commande peut être annulée (en entier ou ligne par ligne) tant qu'elle n'est pas expédiée. */
    public boolean estAnnulable() {
        return this == CONFIRMEE || this == EN_PREPARATION;
    }
}
