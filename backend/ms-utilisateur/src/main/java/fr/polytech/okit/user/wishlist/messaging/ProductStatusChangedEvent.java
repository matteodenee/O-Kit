package fr.polytech.okit.user.wishlist.messaging;

import java.time.Instant;
import java.util.UUID;

public record ProductStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        Long productId,
        String previousStatus,
        String newStatus) {
}
