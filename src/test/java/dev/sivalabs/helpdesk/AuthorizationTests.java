package dev.sivalabs.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

@Import(AuthorizationTests.ProbeConfig.class)
class AuthorizationTests extends BaseIT {

    @Autowired
    Probe probe;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/residents", "/admin/staff", "/analytics"})
    void shouldRedirectAnonymousToLogin(String uri) {
        assertThat(mvc.get().uri(uri)).hasStatus3xxRedirection().redirectedUrl().endsWith("/login");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/residents", "/admin/staff", "/analytics"})
    @WithMockUser(roles = "STAFF")
    void shouldForbidStaffFromAdminAreas(String uri) {
        assertThat(mvc.get().uri(uri)).hasStatus(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/residents", "/admin/staff", "/analytics"})
    @WithMockUser(roles = "USER")
    void shouldForbidResidentsFromAdminAreas(String uri) {
        assertThat(mvc.get().uri(uri)).hasStatus(403);
    }

    @Test
    void shouldEnforceAccessPolicyInPreAuthorize() {
        signIn(new SecurityUser(10L, "Res", "res@example.com", "x", Role.ROLE_USER));
        assertThat(probe.createTicket()).isEqualTo("ok");

        signIn(new SecurityUser(11L, "Staff", "staff@example.com", "x", Role.ROLE_STAFF));
        assertThatThrownBy(probe::createTicket).isInstanceOf(AccessDeniedException.class);
    }

    private static void signIn(SecurityUser user) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    static class Probe {
        @PreAuthorize("@accessPolicy.canCreateTicket(principal)")
        public String createTicket() {
            return "ok";
        }
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        Probe probe() {
            return new Probe();
        }
    }
}
