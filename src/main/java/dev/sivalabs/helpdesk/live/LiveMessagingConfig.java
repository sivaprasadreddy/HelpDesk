package dev.sivalabs.helpdesk.live;

import dev.sivalabs.helpdesk.tickets.TicketMessagingConfig;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Each application instance gets its own server-named, auto-deleted queue of every ticket event, so it can tell the
 * browsers connected to it. A missed event only means a page refreshes later, so nothing is dead-lettered.
 */
@Configuration
@EnableScheduling
class LiveMessagingConfig {

    @Bean
    Queue liveTicketEventsQueue() {
        return new AnonymousQueue();
    }

    @Bean
    Binding liveTicketEventsBinding(Queue liveTicketEventsQueue, TopicExchange ticketsExchange) {
        return BindingBuilder.bind(liveTicketEventsQueue).to(ticketsExchange).with(TicketMessagingConfig.ALL_EVENTS);
    }
}
