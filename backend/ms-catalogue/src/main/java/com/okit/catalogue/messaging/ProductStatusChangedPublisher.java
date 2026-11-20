package com.okit.catalogue.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "catalogue.messaging.enabled", havingValue = "true")
public class ProductStatusChangedPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ProductStatusChangedPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ProductStatusChangedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(
                    CatalogueMessagingTopology.EXCHANGE,
                    CatalogueMessagingTopology.PRODUCT_STATUS_CHANGED_ROUTING_KEY,
                    payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de sérialiser l'événement de statut produit", exception);
        }
    }
}
