package dev.sivalabs.helpdesk.live;

import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.SecurityUser;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The browsers connected to this instance, each with the user it is signed in as. A change goes to those who may view
 * the ticket ({@link AccessPolicy#canViewTicket}) and to its previous assignee, as a {@code ticket} event carrying
 * only the ticket id and what happened.
 */
@Component
class LiveUpdateHub {
    private static final Logger LOG = LoggerFactory.getLogger(LiveUpdateHub.class);
    static final Duration TIMEOUT = Duration.ofMinutes(30);
    static final long RECONNECT_MILLIS = 5_000;

    /** Where a change is delivered; the SSE connection in production, a recorder in tests. */
    interface Subscriber {
        void send(LiveTicketChange change) throws IOException;

        void heartbeat() throws IOException;

        void close();
    }

    private final Map<Subscriber, SecurityUser> subscribers = new ConcurrentHashMap<>();
    private final AccessPolicy accessPolicy;

    LiveUpdateHub(AccessPolicy accessPolicy, MeterRegistry meterRegistry) {
        this.accessPolicy = accessPolicy;
        Gauge.builder("helpdesk.live.connections", subscribers, Map::size)
                .description("Browsers listening for live ticket updates")
                .register(meterRegistry);
    }

    SseEmitter subscribe(SecurityUser user) {
        var emitter = new SseEmitter(TIMEOUT.toMillis());
        var subscriber = new EmitterSubscriber(emitter);
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> remove(subscriber));
        emitter.onError(e -> remove(subscriber));
        try {
            emitter.send(SseEmitter.event().reconnectTime(RECONNECT_MILLIS).comment("connected"));
            subscribers.put(subscriber, user);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    void add(Subscriber subscriber, SecurityUser user) {
        subscribers.put(subscriber, user);
    }

    void remove(Subscriber subscriber) {
        if (subscribers.remove(subscriber) != null) {
            subscriber.close();
        }
    }

    int size() {
        return subscribers.size();
    }

    /** Closes every connection; browsers reconnect on their own. */
    void closeAll() {
        subscribers.keySet().forEach(this::remove);
    }

    void broadcast(LiveTicketChange change) {
        subscribers.forEach((subscriber, user) -> {
            if (concerns(user, change)) {
                try {
                    subscriber.send(change);
                } catch (IOException | IllegalStateException e) {
                    LOG.debug("Dropping a live update connection: {}", e.getMessage());
                    remove(subscriber);
                }
            }
        });
    }

    private boolean concerns(SecurityUser user, LiveTicketChange change) {
        return accessPolicy.canViewTicket(user, change.createdBy(), change.assignedTo())
                || Objects.equals(user.getId(), change.previousAssigneeId());
    }

    /** Keeps idle connections open through proxies, and finds the ones whose browser has gone. */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    void heartbeat() {
        subscribers.keySet().forEach(subscriber -> {
            try {
                subscriber.heartbeat();
            } catch (IOException | IllegalStateException e) {
                remove(subscriber);
            }
        });
    }

    private record EmitterSubscriber(SseEmitter emitter) implements Subscriber {
        @Override
        public void send(LiveTicketChange change) throws IOException {
            emitter.send(SseEmitter.event()
                    .name("ticket")
                    .data(Map.of("ticketId", change.ticketId(), "kind", change.kind()), MediaType.APPLICATION_JSON));
        }

        @Override
        public void heartbeat() throws IOException {
            emitter.send(SseEmitter.event().comment("ping"));
        }

        @Override
        public void close() {
            emitter.complete();
        }
    }
}
