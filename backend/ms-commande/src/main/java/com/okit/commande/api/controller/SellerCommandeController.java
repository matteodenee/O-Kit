package com.okit.commande.api.controller;

import com.okit.commande.api.dto.CommandeDto;
import com.okit.commande.api.dto.Requetes.ChangerStatut;
import com.okit.commande.domain.service.CommandeService;
import com.okit.commande.persistence.entity.StatutCommande;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

/** Suivi des commandes par le commerçant (rôle SELLER). */
@RestController
@RequestMapping("/api/v1/seller/commandes")
@RequiredArgsConstructor
public class SellerCommandeController {

    private final CommandeService commandeService;

    @GetMapping
    public PagedModel<CommandeDto> lister(
            @RequestParam(required = false) StatutCommande statut,
            @PageableDefault(size = 20, sort = "dateCommande", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(commandeService.listerPourVendeur(statut, pageable));
    }

    @GetMapping("/{idCommande}")
    public CommandeDto detail(@PathVariable Long idCommande) {
        return commandeService.detailPourVendeur(idCommande);
    }

    /** EN_PREPARATION, EXPEDIEE (code de suivi obligatoire), LIVREE ou ANNULEE. */
    @PatchMapping("/{idCommande}/statut")
    public CommandeDto changerStatut(@PathVariable Long idCommande, @Valid @RequestBody ChangerStatut demande) {
        return commandeService.changerStatut(idCommande, demande);
    }
}
