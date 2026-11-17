package com.okit.stock.api.controller;

import com.okit.stock.api.dto.StockCheckDto;
import com.okit.stock.api.dto.StockStatusDto;
import com.okit.stock.domain.service.StockService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
@Validated
public class ClientStockController {

    private final StockService service;

    @GetMapping
    public List<StockStatusDto> statuses(@RequestParam List<Long> variantIds) {
        return service.statuses(variantIds);
    }

    @GetMapping("/check")
    public StockCheckDto check(@RequestParam Long variantId, @RequestParam @Min(1) int qty) {
        return service.check(variantId, qty);
    }
}
