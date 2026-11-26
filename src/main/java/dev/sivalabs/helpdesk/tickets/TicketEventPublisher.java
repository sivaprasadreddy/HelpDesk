package dev.sivalabs.helpdesk.tickets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link TicketEvent}s to the {@value TicketMessagingConfig#EXCHANGE} topic exchange, routed by
 * {@link TicketMessagingConfig#routingKey}. Inside a transaction the message is sent only once it commits, so a change
 * that rolls back announces nothing.
 *
 * <p>A message that cannot be sent (the broker is down) is logged and dropped: the change itself stands. Making
 * delivery guaranteed would need an outbox table.
 */
@Component
class TicketEventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(TicketEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    TicketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    void publish(TicketEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(TicketEvent event) {
        var routingKey = TicketMessagingConfig.routingKey(event);
        try {
            rabbitTemplate.convertAndSend(TicketMessagingConfig.EXCHANGE, routingKey, event);
            LOG.debug("Published {} for ticket #{}", routingKey, event.ticket().id());
        } catch (AmqpException e) {
            LOG.error(
                    "Could not publish {} for ticket #{}",
                    routingKey,
                    event.ticket().id(),
                    e);
        }
    }
}
