package dev.sivalabs.helpdesk.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.shared.email.EmailAPI;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

/** Ticket events email the people concerned, with a copy to the administrator. */
@Sql("/test-data.sql")
class TicketNotificationTests extends BaseIT {
    static final String CC = "admin@gmail.com";
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);

    @MockitoBean
    EmailAPI emailAPI;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    void sql(String statement, Object... args) {
        tx.executeWithoutResult(s -> jdbcTemplate.update(statement, args));
    }

    @BeforeEach
    void setUp() {
        sql("""
                insert into users(id, email, password, name, role) values
                (900, 'priya@example.com', 'x', 'Priya Nair', 'ROLE_USER'),
                (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF'),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF')
                """);
        sql("insert into staff_categories(user_id, category_id) values (901, 2), (903, 2)");
        sql("""
                insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)
                values (1, 'Power outage', 'No power', 'HIGH', 'OPEN', 2, 900, 901)
                """);
        clearInvocations(emailAPI);
    }

    record Sent(List<String> to, List<String> cc, String subject, String body) {}

    @SuppressWarnings("unchecked")
    Sent awaitEmail(String subjectStart) {
        var to = ArgumentCaptor.forClass(List.class);
        var cc = ArgumentCaptor.forClass(List.class);
        var subject = ArgumentCaptor.forClass(String.class);
        var body = ArgumentCaptor.forClass(String.class);
        return await().atMost(Duration.ofSeconds(10))
                .until(
                        () -> {
                            try {
                                verify(emailAPI, atLeastOnce())
                                        .sendHtml(
                                                anyString(),
                                                to.capture(),
                                                cc.capture(),
                                                subject.capture(),
                                                body.capture());
                            } catch (AssertionError e) {
                                return null;
                            }
                            for (int i = subject.getAllValues().size() - 1; i >= 0; i--) {
                                if (subject.getAllValues().get(i).startsWith(subjectStart)) {
                                    return new Sent(
                                            to.getAllValues().get(i),
                                            cc.getAllValues().get(i),
                                            subject.getAllValues().get(i),
                                            body.getAllValues().get(i));
                                }
                            }
                            return null;
                        },
                        sent -> sent != null);
    }

    @Test
    void creatingATicketEmailsRaiserAndAssignee() {
        mvc.post()
                .uri("/tickets")
                .with(user(PRIYA))
                .with(csrf())
                .param("title", "Lights flickering")
                .param("description", "Corridor B")
                .param("priority", "LOW")
                .param("categoryId", "2")
                .exchange();

        var sent = awaitEmail("Ticket #");
        assertThat(sent.subject()).endsWith("created: Lights flickering");
        // Priya raised it herself, so only the assignee (Anita: no open work) is told; admin copied.
        assertThat(sent.to()).containsExactly("anita@example.com");
        assertThat(sent.cc()).containsExactly(CC);
        assertThat(sent.body())
                .contains("<strong>Priya Nair</strong> raised a new ticket.")
                .contains("Anita Sharma")
                .contains("<html");
    }

    @Test
    void statusChangeByAssigneeEmailsRaiser() {
        mvc.post()
                .uri("/tickets/1/status")
                .with(user(RAVI))
                .with(csrf())
                .param("from", "OPEN")
                .param("to", "IN_PROGRESS")
                .exchange();

        var sent = awaitEmail("Ticket #1 is now In progress");
        assertThat(sent.to()).containsExactly("priya@example.com");
        assertThat(sent.cc()).containsExactly(CC);
        assertThat(sent.body()).contains("moved from <strong>Open</strong>").contains("Changed by Ravi Kumar.");
    }

    @Test
    void reassignmentEmailsRaiserNewAndPreviousAssignee() {
        mvc.post()
                .uri("/tickets/1/assignee")
                .with(user(ADMIN))
                .with(csrf())
                .param("staffId", "903")
                .exchange();

        var sent = awaitEmail("Ticket #1 assigned");
        assertThat(sent.to()).containsExactly("priya@example.com", "anita@example.com", "ravi@example.com");
        assertThat(sent.cc()).containsExactly(CC);
        assertThat(sent.body())
                .contains("<strong>Anita Sharma</strong> (previously <span>Ravi Kumar</span>).")
                .contains("Changed by Administrator.");
    }

    @Test
    void editEmailsRaiserAndAssignee() {
        mvc.post()
                .uri("/tickets/1/edit")
                .with(user(ADMIN))
                .with(csrf())
                .param("title", "Power outage in B block")
                .param("description", "No power")
                .param("priority", "CRITICAL")
                .param("categoryId", "2")
                .exchange();

        var sent = awaitEmail("Ticket #1 updated");
        assertThat(sent.to()).containsExactly("priya@example.com", "ravi@example.com");
        assertThat(sent.cc()).containsExactly(CC);
        assertThat(sent.body())
                .contains("<strong>Administrator</strong> updated the ticket")
                .contains("Critical");
    }

    @Test
    void commentEmailsEveryoneConcernedButTheAuthor() {
        mvc.post()
                .uri("/tickets/1/comments")
                .with(user(PRIYA))
                .with(csrf())
                .param("content", "Any update?")
                .exchange();

        var sent = awaitEmail("New comment on ticket #1");
        assertThat(sent.to()).containsExactly("ravi@example.com");
        assertThat(sent.cc()).containsExactly(CC);
        assertThat(sent.body())
                .contains("<strong>Priya Nair</strong> commented:")
                .contains("Any update?")
                .contains("href=\"http://localhost:8080/tickets/1\"");
    }

    @Test
    void deactivatingTheAssigneeEmailsAboutTheHandover() {
        mvc.post()
                .uri("/admin/staff/{id}/deactivate", 901)
                .with(user(ADMIN))
                .with(csrf())
                .exchange();

        var sent = awaitEmail("Ticket #1 assigned");
        assertThat(sent.to()).contains("priya@example.com", "anita@example.com");
        assertThat(sent.body()).contains("previous assignee was deactivated");
    }

    @Test
    void nothingIsSentWhenTheChangeFails() throws InterruptedException {
        mvc.post()
                .uri("/tickets/1/status")
                .with(user(RAVI))
                .with(csrf())
                .param("from", "OPEN")
                .param("to", "CLOSED")
                .exchange();

        Thread.sleep(1000);
        verify(emailAPI, org.mockito.Mockito.never())
                .sendHtml(anyString(), anyList(), anyList(), anyString(), anyString());
    }
}
