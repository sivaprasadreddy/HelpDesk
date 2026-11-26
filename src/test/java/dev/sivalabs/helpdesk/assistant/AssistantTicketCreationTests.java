package dev.sivalabs.helpdesk.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.FakeChatModel;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@Sql("/test-data.sql")
class AssistantTicketCreationTests extends BaseIT {
    static final SecurityUser RESIDENT = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser STAFF = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);

    static final String CREATE_ARGS = """
            {"title": "Kitchen sink leaking", "description": "Water drips under the sink in A-101.",
             "priority": "high", "categoryId": 1}""";

    @Autowired
    FakeChatModel chatModel;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> jdbcTemplate.update(
                "insert into users(id, email, password, name, role) values (900, 'priya@example.com', 'x',"
                        + " 'Priya Nair', 'ROLE_USER'), (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF')"));
    }

    @AfterEach
    void resetModel() {
        chatModel.reset();
    }

    void ask(SecurityUser who, MockHttpSession session, String message) {
        assertThat(mvc.post()
                        .uri("/assistant")
                        .session(session)
                        .with(user(who))
                        .with(csrf())
                        .param("message", message))
                .hasStatus3xxRedirection();
    }

    /** Calls the tool on the first round and reports its result on the next, as a model would. */
    void modelCalls(String tool, String arguments) {
        chatModel.answerWith(prompt -> {
            var result = FakeChatModel.lastToolResult(prompt);
            return result == null ? FakeChatModel.toolCall(tool, arguments) : FakeChatModel.text("Done. " + result);
        });
    }

    String onlyDraftId(MockHttpSession session) {
        var body = mvc.get()
                .uri("/assistant")
                .session(session)
                .with(user(RESIDENT))
                .exchange()
                .getResponse();
        try {
            var matcher = java.util.regex.Pattern.compile("/assistant/drafts/([0-9a-f-]+)/create")
                    .matcher(body.getContentAsString());
            assertThat(matcher.find()).as("a Create ticket button").isTrue();
            return matcher.group(1);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    long ticketCount() {
        return jdbcTemplate.queryForObject("select count(*) from tickets", Long.class);
    }

    @Test
    void shouldCreateATicketOnlyWhenTheUserConfirmsTheDraft() {
        modelCalls("proposeTicket", CREATE_ARGS);
        var session = new MockHttpSession();

        ask(RESIDENT, session, "My kitchen sink is leaking in A-101.");

        assertThat(ticketCount()).isZero();
        assertThat(FakeChatModel.lastToolResult(chatModel.lastPrompt())).contains("NOT created yet");
        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("Draft ticket")
                .contains("Kitchen sink leaking")
                .contains("Plumbing")
                .contains("Create ticket");

        var draftId = onlyDraftId(session);
        assertThat(mvc.post()
                        .uri("/assistant/drafts/{id}/create", draftId)
                        .session(session)
                        .with(user(RESIDENT))
                        .with(csrf()))
                .hasStatus3xxRedirection();
        // Pressing the button again does not create a second ticket.
        mvc.post()
                .uri("/assistant/drafts/{id}/create", draftId)
                .session(session)
                .with(user(RESIDENT))
                .with(csrf())
                .exchange();

        var ticket = jdbcTemplate.queryForMap("select * from tickets");
        assertThat(ticket)
                .containsEntry("title", "Kitchen sink leaking")
                .containsEntry("description", "Water drips under the sink in A-101.")
                .containsEntry("priority", "HIGH")
                .containsEntry("category_id", 1L)
                .containsEntry("created_by", 900L)
                .containsEntry("status", "OPEN");
        var id = ticket.get("id");
        assertThat(chatModel.lastToolNames()).containsExactlyInAnyOrder("listCategories", "proposeTicket");
        assertThat(chatModel.lastPrompt().getSystemMessage().getText())
                .contains("Never say that a ticket has been created");
        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("Ticket #" + id + " &quot;Kitchen sink leaking&quot; has been created")
                .contains("href=\"/tickets/" + id + "\"")
                .doesNotContain("/assistant/drafts/" + draftId + "/create");
    }

    @Test
    void shouldNotCreateADraftFromAnotherConversation() {
        assertThat(mvc.post()
                        .uri("/assistant/drafts/{id}/create", "00000000-0000-0000-0000-000000000000")
                        .session(new MockHttpSession())
                        .with(user(RESIDENT))
                        .with(csrf()))
                .hasStatus(404);
        assertThat(ticketCount()).isZero();
    }

    @Test
    void shouldListActiveCategoriesForTheModel() {
        tx.executeWithoutResult(s -> jdbcTemplate.update("update categories set active = false where id = 7"));
        modelCalls("listCategories", "{}");

        ask(RESIDENT, new MockHttpSession(), "My garden needs trimming.");

        var result = FakeChatModel.lastToolResult(chatModel.lastPrompt());
        assertThat(result).contains("Plumbing").contains("Lift Maintenance").doesNotContain("Gardening");
    }

    @Test
    void shouldReportBadInputBackToTheModel() {
        modelCalls("proposeTicket", """
                {"title": "Lift", "description": "Stuck.", "priority": "URGENT", "categoryId": 6}""");

        ask(RESIDENT, new MockHttpSession(), "Raise it.");

        assertThat(FakeChatModel.lastToolResult(chatModel.lastPrompt()))
                .contains("priority must be one of LOW, MEDIUM, HIGH, CRITICAL");
        assertThat(ticketCount()).isZero();
    }

    @Test
    void shouldRefuseAnInactiveCategory() {
        tx.executeWithoutResult(s -> jdbcTemplate.update("update categories set active = false where id = 1"));
        modelCalls("proposeTicket", CREATE_ARGS);

        ask(RESIDENT, new MockHttpSession(), "Raise it.");

        assertThat(FakeChatModel.lastToolResult(chatModel.lastPrompt())).contains("not an active category");
    }

    @Test
    void shouldReportACategoryDeactivatedAfterTheDraft() {
        modelCalls("proposeTicket", CREATE_ARGS);
        var session = new MockHttpSession();
        ask(RESIDENT, session, "My kitchen sink is leaking.");
        var draftId = onlyDraftId(session);
        tx.executeWithoutResult(s -> jdbcTemplate.update("update categories set active = false where id = 1"));

        mvc.post()
                .uri("/assistant/drafts/{id}/create", draftId)
                .session(session)
                .with(user(RESIDENT))
                .with(csrf())
                .exchange();

        assertThat(ticketCount()).isZero();
        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("its category is no longer active");
    }

    @Test
    void shouldNotOfferTicketToolsToStaff() {
        ask(STAFF, new MockHttpSession(), "The lift is broken.");

        assertThat(chatModel.lastToolNames()).isEmpty();
        assertThat(chatModel.lastPrompt().getSystemMessage().getText()).contains("You cannot raise tickets");
        assertThat(mvc.get().uri("/assistant").with(user(STAFF))).bodyText().doesNotContain("draft a ticket for you");
    }
}
