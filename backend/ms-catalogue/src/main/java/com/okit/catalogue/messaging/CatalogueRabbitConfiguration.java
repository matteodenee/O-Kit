package com.okit.catalogue.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "catalogue.messaging.enabled", havingValue = "true")
public class CatalogueRabbitConfiguration {

    @Bean
    TopicExchange catalogueEventsExchange() {
        return new TopicExchange(CatalogueMessagingTopology.EXCHANGE, true, false);
    }
}
