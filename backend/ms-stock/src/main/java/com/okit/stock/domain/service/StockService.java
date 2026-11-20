package com.okit.stock.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okit.stock.api.dto.*;
import com.okit.stock.domain.exception.NotFoundException;
import com.okit.stock.persistence.entity.IdempotentRequest;
import com.okit.stock.persistence.entity.IdempotentRequest.Operation;
import com.okit.stock.persistence.entity.Notification;
import com.okit.stock.persistence.entity.Stock;
import com.okit.stock.persistence.entity.StockMovement;
import com.okit.stock.persistence.entity.StockMovement.Reason;
import com.okit.stock.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StockService {

    /** Résultat rejouable d'une opération idempotente. */
    public record Outcome(int status, String body) {}

    private final StockRepository stocks;
    private final StockMovementRepository movements;
    private final NotificationRepository notifications;
    private final IdempotentRequestRepository idempotency;
    private final ObjectMapper mapper;

    // ---------- Lecture ----------

    static StockStatus statusOf(int stock, int threshold) {
        if (stock <= 0) return StockStatus.OUT_OF_STOCK;
        return stock < threshold ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    }

    @Transactional(readOnly = true)
    public List<StockStatusDto> statuses(Collection<Long> variantIds) {
        Map<Long, Stock> byId = new HashMap<>();
        stocks.findByIdVarianteIn(variantIds).forEach(s -> byId.put(s.getIdVariante(), s));
        return variantIds.stream().distinct().map(id -> {
            Stock s = byId.get(id);
            return new StockStatusDto(id, s == null ? StockStatus.OUT_OF_STOCK
                    : statusOf(s.getStockActuel(), s.getSeuilStockAlerte()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public StockCheckDto check(Long variantId, int qty) {
        Optional<Stock> s = stocks.findByIdVariante(variantId);
        if (s.isEmpty()) return new StockCheckDto(variantId, false, StockStatus.OUT_OF_STOCK);
        Stock st = s.get();
        return new StockCheckDto(variantId, st.getStockActuel() >= qty,
                statusOf(st.getStockActuel(), st.getSeuilStockAlerte()));
    }

    @Transactional(readOnly = true)
    public Page<StockDto> list(Pageable pageable) {
        return stocks.findAll(pageable).map(this::toDto);
    }

    private StockDto toDto(Stock s) {
        return new StockDto(s.getIdVariante(), s.getStockActuel(), s.getSeuilStockAlerte(),
                statusOf(s.getStockActuel(), s.getSeuilStockAlerte()));
    }

    // ---------- Vendeur ----------

    /** Upsert idempotent : si la ligne existe déjà, elle est renvoyée telle quelle. */
    @Transactional
    public StockDto create(CreateStockRequest req) {
        Optional<Stock> existing = stocks.findByIdVariante(req.variantId());
        if (existing.isPresent()) return toDto(existing.get());

        Stock s = new Stock();
        s.setIdVariante(req.variantId());
        s.setStockActuel(req.quantity());
        if (req.threshold() != null) s.setSeuilStockAlerte(req.threshold());
        s.setAlertActive(req.quantity() < s.getSeuilStockAlerte()); // pas de notification à la création
        s = stocks.saveAndFlush(s);
        if (req.quantity() > 0) recordMovement(s, req.quantity(), Reason.INITIAL, null);
        return toDto(s);
    }

    @Transactional
    public StockDto restock(Long variantId, int quantity) {
        Stock s = stocks.lockByIdVariante(variantId)
                .orElseThrow(() -> new NotFoundException("Aucun stock pour la variante " + variantId));
        applyChange(s, quantity, Reason.RESTOCK, null);
        return toDto(s);
    }

    @Transactional
    public StockDto updateThreshold(Long variantId, int threshold) {
        Stock s = stocks.lockByIdVariante(variantId)
                .orElseThrow(() -> new NotFoundException("Aucun stock pour la variante " + variantId));
        s.setSeuilStockAlerte(threshold);
        if (s.getStockActuel() >= threshold) s.setAlertActive(false);
        return toDto(s);
    }

    // ---------- Interne : décrémentation / annulation ----------

    @Transactional
    public Outcome decrement(OrderStockRequest req) {
        String key = req.orderId();
        Optional<IdempotentRequest> replay = idempotency.findById(key);
        if (replay.isPresent()) return new Outcome(replay.get().getResponseStatus(), replay.get().getResponseBody());

        Map<Long, Integer> wanted = merge(req.lines()); // TreeMap : ordre trié
        Map<Long, Stock> locked = lockSorted(wanted.keySet());

        List<Map<String, Object>> shortages = new ArrayList<>();
        wanted.forEach((id, q) -> {
            int available = locked.containsKey(id) ? locked.get(id).getStockActuel() : 0;
            if (available < q) shortages.add(Map.of("variantId", id, "requested", q, "available", available));
        });

        Outcome outcome;
        if (!shortages.isEmpty()) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Stock insuffisant");
            pd.setProperty("orderId", key);
            pd.setProperty("shortages", shortages);
            outcome = new Outcome(409, json(pd)); // rien n'a été modifié
        } else {
            wanted.forEach((id, q) -> applyChange(locked.get(id), -q, Reason.ORDER_PAID, key));
            outcome = new Outcome(200, json(Map.of("orderId", key, "status", "DECREMENTED")));
        }
        remember(key, Operation.DECREMENT, outcome);
        return outcome;
    }

    @Transactional
    public Outcome release(OrderStockRequest req) {
        String key = "release:" + req.orderId(); // clé distincte de celle de decrement
        Optional<IdempotentRequest> replay = idempotency.findById(key);
        if (replay.isPresent()) return new Outcome(replay.get().getResponseStatus(), replay.get().getResponseBody());

        Map<Long, Integer> wanted = merge(req.lines());
        Map<Long, Stock> locked = lockSorted(wanted.keySet());
        wanted.keySet().forEach(id -> {
            if (!locked.containsKey(id)) throw new NotFoundException("Aucun stock pour la variante " + id);
        });
        wanted.forEach((id, q) -> applyChange(locked.get(id), q, Reason.ORDER_CANCELLED, req.orderId()));

        Outcome outcome = new Outcome(200, json(Map.of("orderId", req.orderId(), "status", "RELEASED")));
        remember(key, Operation.RELEASE, outcome);
        return outcome;
    }

    // ---------- Notifications ----------

    @Transactional(readOnly = true)
    public Page<NotificationDto> notifications(boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notifications.findByReadFalseOrderByCreatedAtDesc(pageable)
                : notifications.findAllByOrderByCreatedAtDesc(pageable);
        return page.map(n -> new NotificationDto(n.getId(), n.getIdVariante(), n.getType().name(),
                n.getMessage(), n.getStockActuel(), n.getSeuil(), n.isRead(), n.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public long unreadCount() { return notifications.countByReadFalse(); }

    @Transactional
    public void markRead(Long id) {
        Notification n = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification " + id + " introuvable"));
        if (!n.isRead()) { n.setRead(true); n.setReadAt(Instant.now()); }
    }

    // ---------- Internes ----------

    private Map<Long, Integer> merge(List<OrderLine> lines) {
        Map<Long, Integer> m = new TreeMap<>();
        lines.forEach(l -> m.merge(l.variantId(), l.quantity(), Integer::sum));
        return m;
    }

    /** SELECT ... FOR UPDATE dans l'ordre trié de id_variante (anti-deadlock). */
    private Map<Long, Stock> lockSorted(Set<Long> ids) {
        Map<Long, Stock> m = new LinkedHashMap<>();
        stocks.lockAllByIdVarianteIn(ids).forEach(s -> m.put(s.getIdVariante(), s));
        return m;
    }

    /** Applique le mouvement, et gère l'alerte dans la même transaction. */
    private void applyChange(Stock s, int delta, Reason reason, String ref) {
        int before = s.getStockActuel();
        int after = before + delta;
        s.setStockActuel(after);
        recordMovement(s, delta, reason, ref);

        int threshold = s.getSeuilStockAlerte();
        if (after < threshold) {
            boolean crossing = !s.isAlertActive();
            boolean nowEmpty = after == 0 && before > 0;
            if (crossing || nowEmpty) {
                s.setAlertActive(true);
                notify(s, after == 0 ? Notification.Type.OUT_OF_STOCK : Notification.Type.LOW_STOCK);
            }
        } else if (s.isAlertActive()) {
            s.setAlertActive(false); // réapprovisionnement : l'alerte pourra se redéclencher
        }
    }

    private void recordMovement(Stock s, int delta, Reason reason, String ref) {
        StockMovement m = new StockMovement();
        m.setStockId(s.getId());
        m.setIdVariante(s.getIdVariante());
        m.setQuantityChange(delta);
        m.setStockAfter(s.getStockActuel());
        m.setReason(reason);
        m.setReferenceId(ref);
        movements.save(m);
    }

    private void notify(Stock s, Notification.Type type) {
        Notification n = new Notification();
        n.setIdVariante(s.getIdVariante());
        n.setType(type);
        n.setStockActuel(s.getStockActuel());
        n.setSeuil(s.getSeuilStockAlerte());
        n.setMessage(type == Notification.Type.OUT_OF_STOCK
                ? "Rupture de stock pour la variante " + s.getIdVariante()
                : "Stock bas (" + s.getStockActuel() + ") pour la variante " + s.getIdVariante());
        notifications.save(n);
    }

    private void remember(String key, Operation op, Outcome outcome) {
        IdempotentRequest r = new IdempotentRequest();
        r.setIdempotencyKey(key);
        r.setOperation(op);
        r.setResponseStatus(outcome.status());
        r.setResponseBody(outcome.body());
        idempotency.saveAndFlush(r);
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}