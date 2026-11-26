package dev.sivalabs.helpdesk.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

/** Logs sign-ins, failed sign-ins and sign-outs, by user id (never the password or the typed email). */
@Component
class AuthenticationEventsLogger {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationEventsLogger.class);

    @EventListener
    public void on(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof SecurityUser user) {
            LOG.info("User {} ({}) signed in", user.getId(), user.getRole());
        }
    }

    @EventListener
    public void on(AbstractAuthenticationFailureEvent event) {
        LOG.warn("Failed sign-in attempt: {}", event.getException().getMessage());
    }

    @EventListener
    public void on(LogoutSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof SecurityUser user) {
            LOG.info("User {} signed out", user.getId());
        }
    }
}
