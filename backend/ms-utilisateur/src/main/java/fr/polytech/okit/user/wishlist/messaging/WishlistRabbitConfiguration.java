package fr.polytech.okit.user.wishlist.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "wishlist.messaging.enabled", havingValue = "true")
public class WishlistRabbitConfiguration {

    @Bean
    TopicExchange catalogueEventsExchange() {
        return new TopicExchange(WishlistMessagingTopology.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange catalogueEventsDeadLetterExchange() {
        return new DirectExchange(WishlistMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue productStatusChangedQueue() {
        return QueueBuilder.durable(WishlistMessagingTopology.QUEUE)
                .deadLetterExchange(WishlistMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(WishlistMessagingTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue productStatusChangedDeadLetterQueue() {
        return QueueBuilder.durable(WishlistMessagingTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding productStatusChangedBinding(
            @Qualifier("productStatusChangedQueue") Queue productStatusChangedQueue,
            TopicExchange catalogueEventsExchange) {
        return BindingBuilder.bind(productStatusChangedQueue)
                .to(catalogueEventsExchange)
                .with(WishlistMessagingTopology.ROUTING_KEY);
    }

    @Bean
    Binding productStatusChangedDeadLetterBinding(
            @Qualifier("productStatusChangedDeadLetterQueue") Queue productStatusChangedDeadLetterQueue,
            DirectExchange catalogueEventsDeadLetterExchange) {
        return BindingBuilder.bind(productStatusChangedDeadLetterQueue)
                .to(catalogueEventsDeadLetterExchange)
                .with(WishlistMessagingTopology.DEAD_LETTER_ROUTING_KEY);
    }
}
