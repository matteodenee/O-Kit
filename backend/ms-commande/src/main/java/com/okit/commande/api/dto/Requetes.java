package com.okit.commande.api.dto;

import com.okit.commande.persistence.entity.StatutCommande;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Corps JSON reçus par l'API. Les annotations déclenchent un 400 automatique si invalide. */
public final class Requetes {

    private Requetes() {
    }

    /** POST /api/v1/panier/lignes */
    public record AjoutLigne(
            @NotNull Long idProduit,
            @NotNull Long idVariante,
            @NotNull @Min(1) @Max(10) Integer quantite,
            @Valid FlocageDemande flocage          // optionnel
    ) {
    }

    public record FlocageDemande(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[\\p{L} .'-]+$",
                    message = "lettres, espaces, point, apostrophe et tiret uniquement") String texte,
            @NotNull @Min(0) @Max(99) Integer numero
    ) {
    }

    /** PATCH /api/v1/panier/lignes/{idLigne} */
    public record ModifierQuantite(@NotNull @Min(1) @Max(10) Integer quantite) {
    }

    public record Adresse(
            @NotBlank @Size(max = 255) String rue,
            @Size(max = 255) String complement,
            @NotBlank @Pattern(regexp = "\\d{5}", message = "5 chiffres") String codePostal,
            @NotBlank @Size(max = 100) String ville
    ) {
    }

    /** POST /api/v1/commandes : transforme le panier en commande */
    public record ValiderCommande(@NotNull @Valid Adresse adresseLivraison) {
    }

    /** PATCH /api/v1/commandes/{id} */
    public record ModifierCommande(@NotNull @Valid Adresse adresseLivraison) {
    }

    /** PATCH /api/v1/seller/commandes/{id}/statut */
    public record ChangerStatut(@NotNull StatutCommande statut, @Size(max = 100) String codeSuiviTransporteur) {
    }
}
