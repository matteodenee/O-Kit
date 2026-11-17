package com.okit.stock.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    public enum Reason { INITIAL, ORDER_PAID, SELLER_ADJUSTMENT, RESTOCK, ORDER_CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "id_variante", nullable = false)
    private Long idVariante;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
