package com.okit.catalogue.messaging;

import com.okit.catalogue.entities.ProduitStatut;

import java.time.Instant;
import java.util.UUID;

public record ProductStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        Long productId,
        ProduitStatut previousStatus,
        ProduitStatut newStatus) {

    public static ProductStatusChangedEvent create(
            Long productId,
            ProduitStatut previousStatus,
            ProduitStatut newStatus) {
        return new ProductStatusChangedEvent(
                UUID.randomUUID(),
                Instant.now(),
                productId,
                previousStatus,
                newStatus);
    }
}
