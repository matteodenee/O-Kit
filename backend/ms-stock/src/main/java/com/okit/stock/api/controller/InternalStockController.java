package com.okit.stock.api.controller;

import com.okit.stock.api.dto.OrderStockRequest;
import com.okit.stock.domain.service.StockService;
import com.okit.stock.domain.service.StockService.Outcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/stocks")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockService service;

    @PostMapping("/decrement")
    public ResponseEntity<String> decrement(@Valid @RequestBody OrderStockRequest req) {
        return toResponse(service.decrement(req));
    }

    @PostMapping("/release")
    public ResponseEntity<String> release(@Valid @RequestBody OrderStockRequest req) {
        return toResponse(service.release(req));
    }

    private ResponseEntity<String> toResponse(Outcome o) {
        MediaType type = o.status() == 200 ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_PROBLEM_JSON;
        return ResponseEntity.status(o.status()).contentType(type).body(o.body());
    }
}