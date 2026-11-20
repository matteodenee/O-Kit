package fr.polytech.okit.user.wishlist.messaging;

import fr.polytech.okit.user.wishlist.WishlistService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
@ConditionalOnProperty(name = "wishlist.messaging.enabled", havingValue = "true")
public class ProductStatusChangedListener {

    private static final Set<String> SUPPORTED_STATUSES = Set.of("ACTIF", "DESACTIVE");

    private final ObjectMapper objectMapper;
    private final WishlistService wishlistService;

    public ProductStatusChangedListener(ObjectMapper objectMapper, WishlistService wishlistService) {
        this.objectMapper = objectMapper;
        this.wishlistService = wishlistService;
    }

    @RabbitListener(queues = WishlistMessagingTopology.QUEUE)
    public void handle(String payload) throws JacksonException {
        ProductStatusChangedEvent event = objectMapper.readValue(payload, ProductStatusChangedEvent.class);
        validate(event);
        if ("DESACTIVE".equals(event.newStatus())) {
            wishlistService.removeProductFromAllWishlists(event.productId());
        }
    }

    private static void validate(ProductStatusChangedEvent event) {
        if (event.eventId() == null
                || event.occurredAt() == null
                || event.productId() == null
                || event.productId() <= 0
                || event.previousStatus() == null
                || event.newStatus() == null
                || !SUPPORTED_STATUSES.contains(event.previousStatus())
                || !SUPPORTED_STATUSES.contains(event.newStatus())) {
            throw new IllegalArgumentException("Événement product.status.changed.v1 invalide");
        }
    }
}
