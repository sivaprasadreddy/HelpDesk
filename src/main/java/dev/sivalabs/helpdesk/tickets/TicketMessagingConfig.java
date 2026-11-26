package dev.sivalabs.helpdesk.tickets;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tickets module's RabbitMQ contract: every {@link TicketEvent} goes to the {@value #EXCHANGE} topic exchange
 * with a routing key {@code ticket.<what happened>}; bind {@code ticket.#} to receive them all.
 */
@Configuration
public class TicketMessagingConfig {
    public static final String EXCHANGE = "helpdesk.tickets";
    public static final String ALL_EVENTS = "ticket.#";

    @Bean
    TopicExchange ticketsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    static String routingKey(TicketEvent event) {
        return switch (event) {
            case TicketCreatedEvent e -> "ticket.created";
            case TicketAssignedEvent e -> "ticket.assigned";
            case TicketStatusChangedEvent e -> "ticket.status-changed";
            case TicketUpdatedEvent e -> "ticket.updated";
            case TicketCommentAddedEvent e -> "ticket.comment-added";
        };
    }
}
