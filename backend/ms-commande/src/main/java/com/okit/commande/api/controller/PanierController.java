package com.okit.commande.api.controller;

import com.okit.commande.api.dto.CommandeDto;
import com.okit.commande.api.dto.Requetes.AjoutLigne;
import com.okit.commande.api.dto.Requetes.ModifierQuantite;
import com.okit.commande.domain.service.PanierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Panier de l'acheteur connecté (identifié par le "sub" de son JWT). */
@RestController
@RequestMapping("/api/v1/panier")
@RequiredArgsConstructor
public class PanierController {

    private final PanierService panierService;

    @GetMapping
    public CommandeDto consulter(@AuthenticationPrincipal Jwt jwt) {
        return panierService.consulter(jwt.getSubject());
    }

    /** Ajoute un maillot (taille = idVariante), avec flocage optionnel. */
    @PostMapping("/lignes")
    @ResponseStatus(HttpStatus.CREATED)
    public CommandeDto ajouterLigne(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AjoutLigne demande) {
        return panierService.ajouterLigne(jwt.getSubject(), demande);
    }

    @PatchMapping("/lignes/{idLigne}")
    public CommandeDto modifierQuantite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long idLigne,
                                        @Valid @RequestBody ModifierQuantite demande) {
        return panierService.modifierQuantite(jwt.getSubject(), idLigne, demande);
    }

    @DeleteMapping("/lignes/{idLigne}")
    public CommandeDto supprimerLigne(@AuthenticationPrincipal Jwt jwt, @PathVariable Long idLigne) {
        return panierService.supprimerLigne(jwt.getSubject(), idLigne);
    }

    @DeleteMapping
    public CommandeDto vider(@AuthenticationPrincipal Jwt jwt) {
        return panierService.vider(jwt.getSubject());
    }
}
