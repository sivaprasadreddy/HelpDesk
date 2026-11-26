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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Emails the people concerned by each ticket event, with a copy to the help desk administrator
 * ({@code helpdesk.notifications.cc}). The content is an HTML Thymeleaf template per event under
 * {@code templates/email/}, sharing {@code email/layout.html}. Runs after the change is committed,
 * asynchronously; Spring Modulith keeps the event until the email is sent, so a mail server outage does not lose
 * it.
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
    private final ITemplateEngine templateEngine;
    private final NotificationProperties properties;

    TicketNotificationListener(
            EmailAPI emailAPI, UserAPI userAPI, ITemplateEngine templateEngine, NotificationProperties properties) {
        this.emailAPI = emailAPI;
        this.userAPI = userAPI;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @ApplicationModuleListener
    public void on(TicketCreatedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "Ticket #%d created: %s".formatted(t.id(), t.title()),
                "email/ticket-created",
                Map.of());
    }

    @ApplicationModuleListener
    public void on(TicketAssignedEvent event) {
        var t = event.ticket();
        var vars = new HashMap<String, Object>();
        vars.put("previousAssigneeId", event.previousAssigneeId());
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo(), event.previousAssigneeId()),
                "Ticket #%d assigned: %s".formatted(t.id(), t.title()),
                "email/ticket-assigned",
                vars);
    }

    @ApplicationModuleListener
    public void on(TicketStatusChangedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "Ticket #%d is now %s: %s".formatted(t.id(), t.status().getLabel(), t.title()),
                "email/ticket-status-changed",
                Map.<String, Object>of("previousStatus", event.previousStatus()));
    }

    @ApplicationModuleListener
    public void on(TicketUpdatedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "Ticket #%d updated: %s".formatted(t.id(), t.title()),
                "email/ticket-updated",
                Map.of());
    }

    @ApplicationModuleListener
    public void on(TicketCommentAddedEvent event) {
        var t = event.ticket();
        notify(
                event,
                Arrays.asList(t.createdBy(), t.assignedTo()),
                "New comment on ticket #%d: %s".formatted(t.id(), t.title()),
                "email/ticket-comment-added",
                Map.<String, Object>of("comment", event.comment()));
    }

    /**
     * Every template gets the {@code ticket} snapshot, {@code subject}, {@code ticketUrl} and the names
     * {@code creatorName}, {@code assigneeName} ("Not assigned yet" when nobody), {@code actorName} ({@code null}
     * when the system acted) and, for a reassignment, {@code previousAssigneeName}; plus the event's own
     * {@code extra} variables.
     */
    private void notify(
            TicketEvent event,
            List<@Nullable Long> concerned,
            String subject,
            String template,
            Map<String, Object> extra) {
        var t = event.ticket();
        var ids = new LinkedHashSet<Long>();
        concerned.stream().filter(Objects::nonNull).forEach(ids::add);
        var everyone = new LinkedHashSet<>(ids);
        if (event.actorId() != null) {
            everyone.add(event.actorId());
        }
        var contacts = userAPI.findContactsByIds(everyone);

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

        var context = new Context(Locale.ENGLISH);
        context.setVariables(extra);
        context.setVariable("ticket", t);
        context.setVariable("subject", subject);
        context.setVariable("ticketUrl", properties.baseUrl() + "/tickets/" + t.id());
        context.setVariable("creatorName", name(contacts, t.createdBy()));
        context.setVariable(
                "assigneeName", t.assignedTo() == null ? "Not assigned yet" : name(contacts, t.assignedTo()));
        context.setVariable("actorName", event.actorId() == null ? null : name(contacts, event.actorId()));
        if (extra.get("previousAssigneeId") instanceof Long previous) {
            context.setVariable("previousAssigneeName", name(userAPI.findContactsByIds(List.of(previous)), previous));
        }
        var html = templateEngine.process(template, context);

        emailAPI.sendHtml(properties.from(), to, cc, subject, html);
        LOG.info("Sent '{}' to {} (cc {})", subject, to, cc);
    }

    private static String name(Map<Long, UserContact> contacts, Long id) {
        var c = contacts.get(id);
        return c == null ? "Unknown" : c.name();
    }
}
