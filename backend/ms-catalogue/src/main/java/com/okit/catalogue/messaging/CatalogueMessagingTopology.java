package com.okit.catalogue.messaging;

public final class CatalogueMessagingTopology {

    public static final String EXCHANGE = "okit.catalogue.events";
    public static final String PRODUCT_STATUS_CHANGED_ROUTING_KEY = "product.status.changed.v1";

    private CatalogueMessagingTopology() {
    }
}
