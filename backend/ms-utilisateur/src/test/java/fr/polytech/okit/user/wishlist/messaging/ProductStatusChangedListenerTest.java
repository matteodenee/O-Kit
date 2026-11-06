package fr.polytech.okit.user.wishlist.messaging;

import fr.polytech.okit.user.wishlist.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ProductStatusChangedListenerTest {

    @Mock
    private WishlistService wishlistService;

    private ObjectMapper objectMapper;
    private ProductStatusChangedListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ProductStatusChangedListener(objectMapper, wishlistService);
    }

    @Test
    void removesDisabledProductFromAllWishlists() throws Exception {
        String payload = objectMapper.writeValueAsString(event("DESACTIVE"));

        listener.handle(payload);

        verify(wishlistService).removeProductFromAllWishlists(42L);
    }

    @Test
    void ignoresActivatedProduct() throws Exception {
        String payload = objectMapper.writeValueAsString(event("ACTIF"));

        listener.handle(payload);

        verify(wishlistService, never()).removeProductFromAllWishlists(42L);
    }

    @Test
    void rejectsEventThatDoesNotRespectTheVersionedContract() {
        String payload = """
                {
                  "eventId": "37e2cda4-f5b7-4c9c-8f95-201ad2640bdd",
                  "occurredAt": "2026-11-05T10:00:00Z",
                  "productId": 42,
                  "previousStatus": "ACTIF"
                }
                """;

        assertThatThrownBy(() -> listener.handle(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product.status.changed.v1");
        verify(wishlistService, never()).removeProductFromAllWishlists(42L);
    }

    private static ProductStatusChangedEvent event(String newStatus) {
        return new ProductStatusChangedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-11-05T10:00:00Z"),
                42L,
                "ACTIF",
                newStatus);
    }
}
