package dev.sivalabs.helpdesk.live;

import dev.sivalabs.helpdesk.tickets.TicketAssignedEvent;
import dev.sivalabs.helpdesk.tickets.TicketCommentAddedEvent;
import dev.sivalabs.helpdesk.tickets.TicketCreatedEvent;
import dev.sivalabs.helpdesk.tickets.TicketEvent;
import dev.sivalabs.helpdesk.tickets.TicketStatusChangedEvent;
import dev.sivalabs.helpdesk.tickets.TicketUpdatedEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Turns the ticket events on this instance's queue into {@link LiveTicketChange}s for the connected browsers. */
@Component
@RabbitListener(queues = "#{liveTicketEventsQueue.name}")
class LiveTicketEventListener {
    private final LiveUpdateHub hub;

    LiveTicketEventListener(LiveUpdateHub hub) {
        this.hub = hub;
    }

    @RabbitHandler
    public void on(TicketCreatedEvent event) {
        broadcast(event, "created", null);
    }

    @RabbitHandler
    public void on(TicketAssignedEvent event) {
        broadcast(event, "assigned", event.previousAssigneeId());
    }

    @RabbitHandler
    public void on(TicketStatusChangedEvent event) {
        broadcast(event, "status-changed", null);
    }

    @RabbitHandler
    public void on(TicketUpdatedEvent event) {
        broadcast(event, "updated", null);
    }

    @RabbitHandler
    public void on(TicketCommentAddedEvent event) {
        broadcast(event, "comment-added", null);
    }

    private void broadcast(TicketEvent event, String kind, @Nullable Long previousAssigneeId) {
        var t = event.ticket();
        hub.broadcast(new LiveTicketChange(t.id(), kind, t.createdBy(), t.assignedTo(), previousAssigneeId));
    }
}
