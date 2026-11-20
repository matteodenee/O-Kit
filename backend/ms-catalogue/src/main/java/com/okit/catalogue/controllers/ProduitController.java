package com.okit.catalogue.controllers;

import com.okit.catalogue.dto.PageResponse;
import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.dto.ProduitUpdateInputDto;
import com.okit.catalogue.dto.StockStatusUpdateDto;
import com.okit.catalogue.entities.ProduitStatut;
import com.okit.catalogue.services.ProduitService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProduitController {

    private final ProduitService produitService;

    public ProduitController(ProduitService produitService) {
        this.produitService = produitService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProduitSummaryDto>> getProducts(
            @RequestParam(name = "equipe_id", required = false) Long equipe_id,
            @RequestParam(name = "equipeId", required = false) Long equipeId,
            @RequestParam(name = "saison_id", required = false) Long saison_id,
            @RequestParam(name = "saisonId", required = false) Long saisonId,
            @RequestParam(name = "marque_id", required = false) Long marque_id,
            @RequestParam(name = "marqueId", required = false) Long marqueId,
            @RequestParam(required = false) ProduitStatut statut,
            @PageableDefault(size = 10, sort = "idProduit") Pageable pageable
    ) {
        Long resolvedEquipeId = equipe_id != null ? equipe_id : equipeId;
        Long resolvedSaisonId = saison_id != null ? saison_id : saisonId;
        Long resolvedMarqueId = marque_id != null ? marque_id : marqueId;

        PageResponse<ProduitSummaryDto> response = produitService.getProducts(
                resolvedEquipeId,
                resolvedSaisonId,
                resolvedMarqueId,
                statut,
                pageable
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProduitDetailDto> getProductById(@PathVariable Long id) {
        ProduitDetailDto product = produitService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @PostMapping
    public ResponseEntity<ProduitDetailDto> createProduct(@Valid @RequestBody ProduitInputDto input) {
        ProduitDetailDto created = produitService.createProduct(input);
        URI location = URI.create("/api/v1/products/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProduitDetailDto> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProduitUpdateInputDto updateInput
    ) {
        ProduitDetailDto updated = produitService.updateProduct(id, updateInput);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/stock-status")
    public ResponseEntity<Map<String, Object>> updateStockStatus(
            @PathVariable Long id,
            @Valid @RequestBody StockStatusUpdateDto stockStatus
    ) {
        produitService.updateStockStatus(id, stockStatus.enStock());
        return ResponseEntity.ok(Map.of(
                "message", "Statut du stock mis à jour avec succès",
                "id", id,
                "enStock", stockStatus.enStock()
        ));
    }
}
