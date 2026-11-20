package com.okit.stock.api.controller;

import com.okit.stock.api.dto.*;
import com.okit.stock.domain.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
public class SellerStockController {

    private final StockService service;

    @GetMapping("/stocks")
    public Page<StockDto> list(@PageableDefault(size = 50, sort = "idVariante") Pageable pageable) {
        return service.list(pageable);
    }

    @PostMapping("/stocks")
    public ResponseEntity<StockDto> create(@Valid @RequestBody CreateStockRequest req) {
        StockDto dto = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/seller/stocks/" + dto.variantId())).body(dto);
    }

    @PostMapping("/stocks/{variantId}/restock")
    public StockDto restock(@PathVariable Long variantId, @Valid @RequestBody RestockRequest req) {
        return service.restock(variantId, req.quantity());
    }

    @PatchMapping("/stocks/{variantId}/threshold")
    public StockDto threshold(@PathVariable Long variantId, @Valid @RequestBody ThresholdRequest req) {
        return service.updateThreshold(variantId, req.threshold());
    }

    @GetMapping("/notifications")
    public Page<NotificationDto> notifications(@RequestParam(defaultValue = "false") boolean unread,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return service.notifications(unread, pageable);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount());
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }
}