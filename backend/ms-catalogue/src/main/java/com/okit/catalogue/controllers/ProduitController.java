package com.okit.catalogue.controllers;

import com.okit.catalogue.dto.CatalogueFiltersDto;
import com.okit.catalogue.dto.PageResponse;
import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.dto.ProduitUpdateInputDto;
import com.okit.catalogue.dto.StockStatusUpdateDto;
import com.okit.catalogue.entities.ProduitStatut;
import com.okit.catalogue.services.ProduitService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProduitController {

    private static final String PRICE_SORT_PROPERTY = "prixDeBase";

    private final ProduitService produitService;

    public ProduitController(ProduitService produitService) {
        this.produitService = produitService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProduitSummaryDto>> getProducts(
            @RequestParam(name = "equipe_id", required = false) Long equipe_id,
            @RequestParam(name = "equipeId", required = false) Long equipeId,
            @RequestParam(name = "equipe", required = false) String equipe,
            @RequestParam(name = "club", required = false) String club,
            @RequestParam(name = "saison_id", required = false) Long saison_id,
            @RequestParam(name = "saisonId", required = false) Long saisonId,
            @RequestParam(name = "saison", required = false) String saison,
            @RequestParam(name = "marque_id", required = false) Long marque_id,
            @RequestParam(name = "marqueId", required = false) Long marqueId,
            @RequestParam(name = "marque", required = false) String marque,
            @RequestParam(required = false) ProduitStatut statut,
            @RequestParam(name = "tri", required = false) String tri,
            @RequestParam(name = "priceSort", required = false) String priceSort,
            @PageableDefault(size = 10, sort = "idProduit") Pageable pageable
    ) {
        Long resolvedEquipeId = equipe_id != null ? equipe_id : equipeId;
        Long resolvedSaisonId = saison_id != null ? saison_id : saisonId;
        Long resolvedMarqueId = marque_id != null ? marque_id : marqueId;
        String resolvedEquipeNom = firstNonBlank(equipe, club);
        String resolvedSaisonNom = normalize(saison);
        String resolvedMarqueNom = normalize(marque);
        Pageable resolvedPageable = resolvePageable(pageable, tri, priceSort);

        PageResponse<ProduitSummaryDto> response;
        if (resolvedEquipeNom == null && resolvedSaisonNom == null && resolvedMarqueNom == null) {
            response = produitService.getProducts(
                    resolvedEquipeId,
                    resolvedSaisonId,
                    resolvedMarqueId,
                    statut,
                    resolvedPageable
            );
        } else {
            response = produitService.getProducts(
                    resolvedEquipeId,
                    resolvedSaisonId,
                    resolvedMarqueId,
                    resolvedEquipeNom,
                    resolvedSaisonNom,
                    resolvedMarqueNom,
                    statut,
                    resolvedPageable
            );
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/filters")
    public ResponseEntity<CatalogueFiltersDto> getFilters() {
        return ResponseEntity.ok(produitService.getAvailableFilters());
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

    private static Pageable resolvePageable(Pageable pageable, String tri, String priceSort) {
        String effectiveTri = firstNonBlank(tri, priceSort);
        if (effectiveTri != null) {
            Sort.Direction direction = parsePriceSortDirection(effectiveTri);
            if (direction != null) {
                return applySort(pageable, Sort.by(direction, PRICE_SORT_PROPERTY));
            }
        }

        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        boolean modified = false;
        List<Sort.Order> mappedOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            Sort.Order mapped = mapPriceOrder(order);
            if (!mapped.equals(order)) {
                modified = true;
            }
            mappedOrders.add(mapped);
        }

        return modified ? applySort(pageable, Sort.by(mappedOrders)) : pageable;
    }

    private static Sort.Order mapPriceOrder(Sort.Order order) {
        String normalizedProp = order.getProperty().trim().toLowerCase(Locale.ROOT);
        return switch (normalizedProp) {
            case "prix", "prixttc", "prixht", "prixdebase", "prix_de_base" ->
                    new Sort.Order(order.getDirection(), PRICE_SORT_PROPERTY);
            case "prix_asc", "price_asc", "prix-asc", "price-asc" ->
                    Sort.Order.asc(PRICE_SORT_PROPERTY);
            case "prix_desc", "price_desc", "prix-desc", "price-desc" ->
                    Sort.Order.desc(PRICE_SORT_PROPERTY);
            default -> order;
        };
    }

    private static Sort.Direction parsePriceSortDirection(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "prix_asc", "price_asc", "asc", "prix-asc", "price-asc" -> Sort.Direction.ASC;
            case "prix_desc", "price_desc", "desc", "prix-desc", "price-desc" -> Sort.Direction.DESC;
            default -> null;
        };
    }

    private static Pageable applySort(Pageable pageable, Sort sort) {
        if (pageable.isPaged()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }
        return Pageable.unpaged(sort);
    }

    private static String firstNonBlank(String primary, String secondary) {
        String normalizedPrimary = normalize(primary);
        return normalizedPrimary != null ? normalizedPrimary : normalize(secondary);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
