package dev.sivalabs.helpdesk.notifications;

import dev.sivalabs.helpdesk.shared.email.EmailAPI;
import dev.sivalabs.helpdesk.tickets.TicketAssignedEvent;
import dev.sivalabs.helpdesk.tickets.TicketCommentAddedEvent;
import dev.sivalabs.helpdesk.tickets.TicketCreatedEvent;
import dev.sivalabs.helpdesk.tickets.TicketEvent;
import dev.sivalabs.helpdesk.tickets.TicketStatusChangedEvent;
import dev.sivalabs.helpdesk.tickets.TicketUpdatedEvent;
import dev.sivalabs.helpdesk.users.UserAPI;
import dev.sivalabs.helpdesk.users.UserContact;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Emails the people concerned by each ticket event, with a copy to the help desk administrator
 * ({@code helpdesk.notifications.cc}). Runs after the change is committed, asynchronously; Spring Modulith keeps
 * the event until the email is sent, so a mail server outage does not lose it.
 *
 * <p>Concerned are the resident who raised the ticket and its assignee, plus the previous assignee on a
 * reassignment; the user who made the change is not told about it. When nobody is left, the administrator gets
 * the email directly.
 */
@Component
@EnableConfigurationProperties(NotificationProperties.class)
class TicketNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(TicketNotificationListener.class);

    private final EmailAPI emailAPI;
    private final UserAPI userAPI;
    private final NotificationProperties properties;

    TicketNotificationListener(EmailAPI emailAPI, UserAPI userAPI, NotificationProperties properties) {
        this.emailAPI = emailAPI;
        this.userAPI = userAPI;
        this.properties = properties;
    }

    @ApplicationModuleListener
    public void on(TicketCreatedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "Ticket #%d created: %s".formatted(t.id(), t.title()),
                names -> """
                        Ticket #%d "%s" was raised by %s.

                        Priority: %s
                        Status: %s
                        Assigned to: %s
                        """.formatted(
                                t.id(),
                                t.title(),
                                names.name(t.createdBy()),
                                t.priority().getLabel(),
                                t.status().getLabel(),
                                names.nameOrNobody(t.assignedTo())));
    }

    @ApplicationModuleListener
    public void on(TicketAssignedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo(), event.previousAssigneeId()),
                "Ticket #%d assigned: %s".formatted(t.id(), t.title()),
                names -> """
                        Ticket #%d "%s" is now assigned to %s%s.
                        %s
                        """.formatted(
                                t.id(),
                                t.title(),
                                names.nameOrNobody(t.assignedTo()),
                                event.previousAssigneeId() == null
                                        ? ""
                                        : " (previously " + names.name(event.previousAssigneeId()) + ")",
                                event.actorId() == null
                                        ? "The previous assignee was deactivated, so the work was handed over."
                                        : "Changed by " + names.name(event.actorId()) + "."));
    }

    @ApplicationModuleListener
    public void on(TicketStatusChangedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "Ticket #%d is now %s: %s".formatted(t.id(), t.status().getLabel(), t.title()),
                names -> """
                        Ticket #%d "%s" moved from %s to %s.
                        Changed by %s.
                        """.formatted(
                                t.id(),
                                t.title(),
                                event.previousStatus().getLabel(),
                                t.status().getLabel(),
                                names.nameOrSystem(event.actorId())));
    }

    @ApplicationModuleListener
    public void on(TicketUpdatedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "Ticket #%d updated: %s".formatted(t.id(), t.title()),
                names -> """
                        The details of ticket #%d "%s" were updated by %s.

                        Priority: %s
                        Status: %s
                        """.formatted(
                                t.id(),
                                t.title(),
                                names.nameOrSystem(event.actorId()),
                                t.priority().getLabel(),
                                t.status().getLabel()));
    }

    @ApplicationModuleListener
    public void on(TicketCommentAddedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "New comment on ticket #%d: %s".formatted(t.id(), t.title()),
                names -> """
                        %s commented on ticket #%d "%s":

                        %s
                        """.formatted(names.nameOrSystem(event.actorId()), t.id(), t.title(), event.comment()));
    }

    private void notify(TicketEvent event, List<Long> concerned, String subject, Body body) {
        var ids = new LinkedHashSet<Long>();
        concerned.stream().filter(Objects::nonNull).forEach(ids::add);
        var everyone = new LinkedHashSet<>(ids);
        if (event.actorId() != null) {
            everyone.add(event.actorId());
        }
        var contacts = userAPI.findContactsByIds(everyone);
        var names = new Names(contacts);

        var to = ids.stream()
                .filter(id -> !id.equals(event.actorId()))
                .map(contacts::get)
                .filter(Objects::nonNull)
                .map(UserContact::email)
                .filter(email -> !email.equalsIgnoreCase(properties.cc()))
                .distinct()
                .toList();
        var cc = List.of(properties.cc());
        if (to.isEmpty()) {
            to = cc;
            cc = List.of();
        }
        var text = body.render(names) + "\nView the ticket: " + properties.baseUrl() + "/tickets/"
                + event.ticket().id() + "\n";
        emailAPI.send(properties.from(), to, cc, subject, text);
        LOG.info("Sent '{}' to {} (cc {})", subject, to, cc);
    }

    @FunctionalInterface
    private interface Body {
        String render(Names names);
    }

    private record Names(Map<Long, UserContact> contacts) {
        String name(@Nullable Long id) {
            var c = id == null ? null : contacts.get(id);
            return c == null ? "Unknown" : c.name();
        }

        String nameOrNobody(@Nullable Long id) {
            return id == null ? "nobody yet (an administrator will assign it)" : name(id);
        }

        String nameOrSystem(@Nullable Long id) {
            return id == null ? "the system" : name(id);
        }
    }
}
