package com.okit.catalogue.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okit.catalogue.entities.ProduitStatut;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

class ProductStatusChangedPublisherTest {

    @Test
    void publishesVersionedJsonOnCatalogueExchange() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ProductStatusChangedPublisher publisher =
                new ProductStatusChangedPublisher(rabbitTemplate, objectMapper);
        ProductStatusChangedEvent event = new ProductStatusChangedEvent(
                UUID.fromString("37e2cda4-f5b7-4c9c-8f95-201ad2640bdd"),
                Instant.parse("2026-11-05T10:00:00Z"),
                42L,
                ProduitStatut.ACTIF,
                ProduitStatut.DESACTIVE);

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq(CatalogueMessagingTopology.EXCHANGE),
                eq(CatalogueMessagingTopology.PRODUCT_STATUS_CHANGED_ROUTING_KEY),
                payloadCaptor.capture());
        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(payload.get("productId").asLong()).isEqualTo(42L);
        assertThat(payload.get("previousStatus").asText()).isEqualTo("ACTIF");
        assertThat(payload.get("newStatus").asText()).isEqualTo("DESACTIVE");
    }
}
