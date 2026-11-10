package com.okit.stock.domain.service;

import com.okit.stock.AbstractIntegrationTest;
import com.okit.stock.api.dto.CreateStockRequest;
import com.okit.stock.api.dto.OrderLine;
import com.okit.stock.api.dto.OrderStockRequest;
import com.okit.stock.api.dto.StockDto;
import com.okit.stock.persistence.repository.NotificationRepository;
import com.okit.stock.persistence.repository.StockMovementRepository;
import com.okit.stock.persistence.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StockServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void cleanDb() {
        notificationRepository.deleteAll();
        movementRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("Décrémentation nominale + rejeu idempotent")
    void testDecrementNominalAndReplay() {
        stockService.create(new CreateStockRequest(101L, 20, 5));

        OrderStockRequest req = new OrderStockRequest("ORDER-001", List.of(new OrderLine(101L, 5)));

        // 1er appel : décrémentation réussie
        StockService.Outcome outcome1 = stockService.decrement(req);
        assertThat(outcome1.status()).isEqualTo(200);

        StockDto stock = stockService.list(org.springframework.data.domain.Pageable.unpaged()).getContent().get(0);
        assertThat(stock.stock()).isEqualTo(15);

        // 2ème appel (rejeu) : renvoie le même statut sans décrémenter à nouveau
        StockService.Outcome outcome2 = stockService.decrement(req);
        assertThat(outcome2.status()).isEqualTo(200);
        assertThat(outcome2.body()).contains("DECREMENTED").contains("ORDER-001");

        stock = stockService.list(org.springframework.data.domain.Pageable.unpaged()).getContent().get(0);
        assertThat(stock.stock()).isEqualTo(15);
    }

    @Test
    @DisplayName("Stock insuffisant : retour 409 tout-ou-rien (pas d'effet partiel)")
    void testDecrementInsufficientStock() {
        stockService.create(new CreateStockRequest(101L, 10, 2));
        stockService.create(new CreateStockRequest(102L, 2, 2));

        OrderStockRequest req = new OrderStockRequest("ORDER-002", List.of(
                new OrderLine(101L, 5),
                new OrderLine(102L, 5)
        ));

        StockService.Outcome outcome = stockService.decrement(req);
        assertThat(outcome.status()).isEqualTo(409);

        assertThat(stockRepository.findByIdVariante(101L).orElseThrow().getStockActuel()).isEqualTo(10);
        assertThat(stockRepository.findByIdVariante(102L).orElseThrow().getStockActuel()).isEqualTo(2);
    }

    @Test
    @DisplayName("Concurrence : accès simultanés sans overselling")
    void testConcurrentDecrement() throws InterruptedException {
        stockService.create(new CreateStockRequest(200L, 10, 2));

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String orderId = "CONC-ORDER-" + i;
            executor.submit(() -> {
                try {
                    latch.await();
                    StockService.Outcome outcome = stockService.decrement(
                            new OrderStockRequest(orderId, List.of(new OrderLine(200L, 3)))
                    );
                    if (outcome.status() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(50);
        }

        assertThat(successCount.get()).isEqualTo(3);
        assertThat(stockRepository.findByIdVariante(200L).orElseThrow().getStockActuel()).isEqualTo(1);
    }
}