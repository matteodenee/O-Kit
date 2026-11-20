package com.okit.stock.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "stocks")
@Getter
@Setter
@NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_variante", nullable = false, unique = true)
    private Long idVariante;

    @Column(name = "stock_actuel", nullable = false)
    private int stockActuel;

    @Column(name = "seuil_stock_alerte", nullable = false)
    private int seuilStockAlerte = 10;

    @Column(name = "alert_active", nullable = false)
    private boolean alertActive;

    @Version
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
