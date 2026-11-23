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
import com.okit.stock.domain.exception.NotFoundException;
import com.okit.stock.persistence.entity.Notification;
import com.okit.stock.persistence.repository.IdempotentRequestRepository;
import com.okit.stock.persistence.entity.StockMovement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockServiceTest extends AbstractIntegrationTest {

    @Autowired
    private IdempotentRequestRepository idempotentRepository;

    @BeforeEach
    void cleanDb() {
        idempotentRepository.deleteAll();
        notificationRepository.deleteAll();
        movementRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
    private void decrement(String orderId, long variantId, int qty) {
        StockService.Outcome o = stockService.decrement(
                new OrderStockRequest(orderId, List.of(new OrderLine(variantId, qty))));
        assertThat(o.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Alerte unique au franchissement du seuil, réarmée après réapprovisionnement")
    void testSingleAlertOnCrossingAndRearm() {
        stockService.create(new CreateStockRequest(300L, 20, 10));

        decrement("ALERT-1", 300L, 5);   // 15 : au-dessus du seuil
        assertThat(notificationRepository.count()).isZero();

        decrement("ALERT-2", 300L, 7);   // 8 : franchissement -> 1 notification
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.findAll().get(0).getType())
                .isEqualTo(Notification.Type.LOW_STOCK);

        decrement("ALERT-3", 300L, 2);   // 6 : toujours sous le seuil -> pas de doublon
        assertThat(notificationRepository.count()).isEqualTo(1);

        stockService.restock(300L, 10);  // 16 : alerte réarmée
        assertThat(stockRepository.findByIdVariante(300L).orElseThrow().isAlertActive()).isFalse();

        decrement("ALERT-4", 300L, 8);   // 8 : nouveau franchissement
        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Passage à zéro : notification OUT_OF_STOCK même si l'alerte stock bas est déjà active")
    void testOutOfStockNotification() {
        stockService.create(new CreateStockRequest(500L, 3, 10)); // déjà sous le seuil, sans notification
        assertThat(notificationRepository.count()).isZero();

        decrement("OOS-1", 500L, 1);     // 2 : alerte déjà active, pas de nouvelle notification
        assertThat(notificationRepository.count()).isZero();

        decrement("OOS-2", 500L, 2);     // 0 : rupture
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.findAll().get(0).getType())
                .isEqualTo(Notification.Type.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("Release : ré-incrémente, rejeu sans double effet, clé distincte de decrement")
    void testReleaseAndReplay() {
        stockService.create(new CreateStockRequest(400L, 10, 2));
        OrderStockRequest req = new OrderStockRequest("ORDER-R1", List.of(new OrderLine(400L, 4)));

        assertThat(stockService.decrement(req).status()).isEqualTo(200);
        assertThat(stockRepository.findByIdVariante(400L).orElseThrow().getStockActuel()).isEqualTo(6);

        StockService.Outcome released = stockService.release(req);
        assertThat(released.status()).isEqualTo(200);
        assertThat(released.body()).contains("RELEASED").contains("ORDER-R1");
        assertThat(stockRepository.findByIdVariante(400L).orElseThrow().getStockActuel()).isEqualTo(10);

        // rejeu du release : aucun nouvel effet
        assertThat(stockService.release(req).status()).isEqualTo(200);
        assertThat(stockRepository.findByIdVariante(400L).orElseThrow().getStockActuel()).isEqualTo(10);
        assertThat(movementRepository.count()).isEqualTo(3); // INITIAL + ORDER_PAID + ORDER_CANCELLED

        // rejeu du decrement après release : renvoie la réponse mémorisée, ne re-décrémente pas
        assertThat(stockService.decrement(req).status()).isEqualTo(200);
        assertThat(stockRepository.findByIdVariante(400L).orElseThrow().getStockActuel()).isEqualTo(10);
    }

    @Test
    @DisplayName("Release avec une variante inconnue : 404 et aucun effet partiel")
    void testReleaseUnknownVariant() {
        stockService.create(new CreateStockRequest(401L, 10, 2));

        OrderStockRequest req = new OrderStockRequest("ORDER-R2", List.of(
                new OrderLine(401L, 3),
                new OrderLine(999L, 1)));

        assertThatThrownBy(() -> stockService.release(req)).isInstanceOf(NotFoundException.class);
        assertThat(stockRepository.findByIdVariante(401L).orElseThrow().getStockActuel()).isEqualTo(10);
        assertThat(idempotentRepository.count()).isZero(); // l'échec n'est pas mémorisé
    }

    @Test
    @DisplayName("Ajustement : valeur cible, mouvement SELLER_ADJUSTMENT, alerte et réarmement")
    void testAdjust() {
        stockService.create(new CreateStockRequest(700L, 20, 10));

        StockDto down = stockService.adjust(700L, 8);   // -12 : franchit le seuil
        assertThat(down.stock()).isEqualTo(8);
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(movementRepository.count()).isEqualTo(2); // INITIAL + ajustement
        assertThat(movementRepository.findAll()).anyMatch(m ->
                m.getReason() == StockMovement.Reason.SELLER_ADJUSTMENT && m.getQuantityChange() == -12);

        stockService.adjust(700L, 8);                    // écart nul : rien ne change
        assertThat(movementRepository.count()).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(1);

        stockService.adjust(700L, 30);                   // remontée : alerte réarmée
        assertThat(stockRepository.findByIdVariante(700L).orElseThrow().isAlertActive()).isFalse();

        stockService.adjust(700L, 0);                    // rupture : notification OUT_OF_STOCK
        assertThat(notificationRepository.count()).isEqualTo(2);

        assertThatThrownBy(() -> stockService.adjust(999L, 5)).isInstanceOf(NotFoundException.class);
    }

}