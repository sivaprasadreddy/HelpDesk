package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/** REQ-021: comments by ADMIN (any ticket), STAFF (assigned) and USER (own). */
@Sql("/test-data.sql")
class TicketCommentsTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser ARJUN = new SecurityUser(902L, "Arjun Rao", "arjun@example.com", "x", Role.ROLE_USER);
    static final SecurityUser ANITA = new SecurityUser(903L, "Anita Sharma", "anita@example.com", "x", Role.ROLE_STAFF);

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
                (902, 'arjun@example.com', 'x', 'Arjun Rao', 'ROLE_USER'),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF')
                """);
        sql("""
                insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)
                values (1, 'Power outage', 'No power', 'HIGH', 'IN_PROGRESS', 2, 900, 901)
                """);
    }

    MvcTestResult comment(SecurityUser who, String content) {
        return mvc.post()
                .uri("/tickets/1/comments")
                .with(user(who))
                .with(csrf())
                .param("content", content)
                .exchange();
    }

    int commentCount() {
        return jdbcTemplate.queryForObject("select count(*) from comments where ticket_id = 1", Integer.class);
    }

    @Test
    void raiserAssigneeAndAdminShouldCommentAndSeeThreadInOrder() {
        assertThat(comment(PRIYA, "  Any update? "))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/tickets/1#comments")
                .flash()
                .containsEntry("successMessage", "Comment added");
        comment(RAVI, "Electrician on the way");
        comment(ADMIN, "Escalated to the power company");

        assertThat(commentCount()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("select content from comments where author_id = 900", String.class))
                .isEqualTo("Any update?");
        assertThat(mvc.get().uri("/tickets/1").with(user(PRIYA)))
                .bodyText()
                .contains("Comments (3)")
                .containsSubsequence("Any update?", "Electrician on the way", "Escalated to the power company")
                .contains("Raised the ticket");
    }

    @Test
    void othersShouldNeitherSeeNorComment() {
        assertThat(comment(ARJUN, "Me too")).hasStatus(404);
        assertThat(comment(ANITA, "Not mine")).hasStatus(404);
        assertThat(commentCount()).isZero();
    }

    @Test
    void shouldRejectEmptyComment() {
        assertThat(comment(RAVI, "   ")).hasStatus3xxRedirection();
        assertThat(commentCount()).isZero();
    }

    @Test
    void emptyCommentShouldShowValidationErrorOnDetailsPage() {
        var flash = mvc.post()
                .uri("/tickets/1/comments")
                .with(user(RAVI))
                .with(csrf())
                .param("content", "")
                .exchange()
                .getMvcResult()
                .getFlashMap();
        assertThat(mvc.get().uri("/tickets/1").with(user(RAVI)).flashAttrs(flash))
                .bodyText()
                .contains("Comment cannot be empty");
    }

    @Test
    void closedTicketShouldTakeNoMoreComments() {
        sql("update tickets set status = 'CLOSED' where id = 1");

        assertThat(comment(PRIYA, "Thanks"))
                .flash()
                .containsEntry("errorMessage", "Ticket #1 is closed and takes no more comments");
        assertThat(commentCount()).isZero();
        assertThat(mvc.get().uri("/tickets/1").with(user(PRIYA)))
                .bodyText()
                .doesNotContain("/tickets/1/comments")
                .contains("This ticket is closed and takes no more comments.");
    }

    @Test
    void shouldShowEmptyThreadWithForm() {
        assertThat(mvc.get().uri("/tickets/1").with(user(RAVI)))
                .bodyText()
                .contains("Comments (0)")
                .contains("No comments yet.")
                .contains("/tickets/1/comments");
    }
}
