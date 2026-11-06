package fr.polytech.okit.user.wishlist.messaging;

public final class WishlistMessagingTopology {

    public static final String EXCHANGE = "okit.catalogue.events";
    public static final String ROUTING_KEY = "product.status.changed.v1";
    public static final String QUEUE = "user-service.product-status.changed.v1";
    public static final String DEAD_LETTER_EXCHANGE = "okit.catalogue.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "user-service.product-status.changed.v1.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "user-service.product-status.changed.v1.failed";

    private WishlistMessagingTopology() {
    }
}
