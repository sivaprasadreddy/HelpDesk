package dev.sivalabs.helpdesk.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import org.junit.jupiter.api.Test;

/** The home page offers shortcuts to the common actions of the user's role. */
class HomeShortcutsTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser STAFF = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser RESIDENT = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);

    @Test
    void residentShouldSeeReportingAndTrackingShortcuts() {
        assertThat(mvc.get().uri("/home").with(user(RESIDENT)))
                .hasStatusOk()
                .bodyText()
                .contains("Shortcuts")
                .contains("Report an issue")
                .contains("href=\"/tickets/mine?status=IN_PROGRESS\"")
                .contains("href=\"/tickets/mine?status=RESOLVED\"")
                .doesNotContain("Ready to start")
                .doesNotContain("Unassigned tickets")
                .doesNotContain("href=\"/admin/analytics\"");
    }

    @Test
    void staffShouldSeeTheirQueue() {
        assertThat(mvc.get().uri("/home").with(user(STAFF)))
                .hasStatusOk()
                .bodyText()
                .contains("Ready to start")
                .contains("href=\"/tickets/assigned?status=OPEN\"")
                .contains("href=\"/tickets/assigned?status=IN_PROGRESS\"")
                .doesNotContain("Report an issue")
                .doesNotContain("Unassigned tickets");
    }

    @Test
    void adminShouldSeeTriageAndManagementShortcuts() {
        assertThat(mvc.get().uri("/home").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("Unassigned tickets")
                .contains("href=\"/tickets?assignee=none\"")
                .contains("href=\"/tickets?status=OPEN&amp;priority=CRITICAL\"")
                .contains("href=\"/admin/analytics\"")
                .contains("href=\"/admin/staff/new\"")
                .contains("href=\"/admin/residents/new\"")
                .doesNotContain("Ready to start")
                .doesNotContain("Report an issue");
    }
}
