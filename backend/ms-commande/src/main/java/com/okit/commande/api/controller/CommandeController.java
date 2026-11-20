package com.okit.commande.api.controller;

import com.okit.commande.api.dto.CommandeDto;
import com.okit.commande.api.dto.Requetes.ModifierCommande;
import com.okit.commande.api.dto.Requetes.ValiderCommande;
import com.okit.commande.domain.service.CommandeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Commandes de l'acheteur connecté. */
@RestController
@RequestMapping("/api/v1/commandes")
@RequiredArgsConstructor
public class CommandeController {

    private final CommandeService commandeService;

    /** CREATE : valide le panier (paiement + décrément du stock) et renvoie la commande créée. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommandeDto valider(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ValiderCommande demande) {
        return commandeService.valider(jwt.getSubject(), demande);
    }

    /** READ : historique des commandes. */
    @GetMapping
    public List<CommandeDto> historique(@AuthenticationPrincipal Jwt jwt) {
        return commandeService.historique(jwt.getSubject());
    }

    /** READ : détail d'une commande. */
    @GetMapping("/{idCommande}")
    public CommandeDto detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long idCommande) {
        return commandeService.detail(jwt.getSubject(), idCommande);
    }

    /** UPDATE : modification côté client (adresse de livraison). */
    @PatchMapping("/{idCommande}")
    public CommandeDto modifier(@AuthenticationPrincipal Jwt jwt, @PathVariable Long idCommande,
                                @Valid @RequestBody ModifierCommande demande) {
        return commandeService.modifier(jwt.getSubject(), idCommande, demande);
    }

    @PostMapping("/{idCommande}/annulation")
    public CommandeDto annuler(@AuthenticationPrincipal Jwt jwt, @PathVariable Long idCommande) {
        return commandeService.annuler(jwt.getSubject(), idCommande);
    }

    /** UPDATE : annule une ligne et recalcule le montant total. */
    @PostMapping("/{idCommande}/lignes/{idLigne}/annulation")
    public CommandeDto annulerLigne(@AuthenticationPrincipal Jwt jwt, @PathVariable Long idCommande,
                                    @PathVariable Long idLigne) {
        return commandeService.annulerLigne(jwt.getSubject(), idCommande, idLigne);
    }
}
