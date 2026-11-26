package dev.sivalabs.helpdesk.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ticket notification emails: the sender, the address copied on every one, and the application's address for
 * links back to the ticket.
 */
@ConfigurationProperties(prefix = "helpdesk.notifications")
record NotificationProperties(
        @DefaultValue("helpdesk@example.com") String from,
        @DefaultValue("admin@gmail.com") String cc,
        @DefaultValue("http://localhost:8080") String baseUrl) {}
