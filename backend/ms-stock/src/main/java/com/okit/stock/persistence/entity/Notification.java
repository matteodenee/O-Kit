package com.okit.stock.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public enum Type { LOW_STOCK, OUT_OF_STOCK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_variante", nullable = false)
    private Long idVariante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "stock_actuel", nullable = false)
    private int stockActuel;

    @Column(nullable = false)
    private int seuil;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}