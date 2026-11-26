package dev.sivalabs.helpdesk.notifications;

import dev.sivalabs.helpdesk.tickets.TicketMessagingConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The notifications module's queue of ticket events, bound to every event on the tickets exchange. A message
 * that still fails after the listener's retries goes to the dead-letter queue {@value #DEAD_LETTER_QUEUE} to be
 * looked at, instead of being redelivered forever.
 */
@Configuration
class NotificationMessagingConfig {
    static final String QUEUE = "helpdesk.notifications.ticket-events";
    static final String DEAD_LETTER_EXCHANGE = "helpdesk.notifications.dlx";
    static final String DEAD_LETTER_QUEUE = "helpdesk.notifications.ticket-events.dlq";

    @Bean
    Queue ticketEventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Binding ticketEventsBinding(Queue ticketEventsQueue, TopicExchange ticketsExchange) {
        return BindingBuilder.bind(ticketEventsQueue).to(ticketsExchange).with(TicketMessagingConfig.ALL_EVENTS);
    }

    @Bean
    DirectExchange notificationsDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue ticketEventsDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding ticketEventsDeadLetterBinding(
            Queue ticketEventsDeadLetterQueue, DirectExchange notificationsDeadLetterExchange) {
        return BindingBuilder.bind(ticketEventsDeadLetterQueue)
                .to(notificationsDeadLetterExchange)
                .with(DEAD_LETTER_QUEUE);
    }
}
