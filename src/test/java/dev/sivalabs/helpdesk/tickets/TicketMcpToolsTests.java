package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sivalabs.helpdesk.BaseIT;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

/** Calls the MCP tools through a real MCP client over HTTP, signed in as different users. */
@Sql("/test-data.sql")
class TicketMcpToolsTests extends BaseIT {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PasswordEncoder passwordEncoder;

    final List<McpSyncClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var password = passwordEncoder.encode("secret123");
        tx.executeWithoutResult(s -> {
            jdbcTemplate.update("update users set password = ? where id = 1", password);
            jdbcTemplate.update(
                    "insert into users(id, email, password, name, role) values"
                            + " (900, 'priya@example.com', ?, 'Priya Nair', 'ROLE_USER'),"
                            + " (902, 'arjun@example.com', ?, 'Arjun Reddy', 'ROLE_USER'),"
                            + " (901, 'ravi@example.com', ?, 'Ravi Kumar', 'ROLE_STAFF')",
                    password,
                    password,
                    password);
            jdbcTemplate.update("insert into staff_categories(user_id, category_id) values (901, 1)");
        });
    }

    @AfterEach
    void closeClients() {
        clients.forEach(McpSyncClient::closeGracefully);
    }

    McpSyncClient client(String email) {
        var token = Base64.getEncoder().encodeToString((email + ":secret123").getBytes(StandardCharsets.UTF_8));
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer(
                        (builder, method, uri, body, context) -> builder.header("Authorization", "Basic " + token))
                .build();
        var client =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
        clients.add(client);
        client.initialize();
        return client;
    }

    static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> args) {
        return client.callTool(new McpSchema.CallToolRequest(tool, args));
    }

    static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    Long createAsResident() {
        var result = call(
                client("priya@example.com"),
                "create_ticket",
                Map.of(
                        "title", "Kitchen sink leaking",
                        "description", "Water drips under the sink in A-101.",
                        "priority", "HIGH",
                        "categoryId", 1));
        assertThat(result.isError()).as(text(result)).isNotEqualTo(Boolean.TRUE);
        return jdbcTemplate.queryForObject("select id from tickets", Long.class);
    }

    @Test
    void shouldListTheTicketTools() {
        var tools = client("priya@example.com").listTools().tools().stream()
                .map(McpSchema.Tool::name)
                .toList();

        assertThat(tools)
                .contains(
                        "list_categories",
                        "list_tickets",
                        "get_ticket",
                        "create_ticket",
                        "add_comment",
                        "change_ticket_status",
                        "assign_ticket");
    }

    @Test
    void shouldRejectClientsThatDoNotSignIn() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        var anonymous =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        clients.add(anonymous);

        assertThatThrownBy(anonymous::initialize).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldCreateATicketAsTheResidentAndAssignIt() {
        var id = createAsResident();

        assertThat(jdbcTemplate.queryForMap("select * from tickets where id = ?", id))
                .containsEntry("created_by", 900L)
                .containsEntry("assigned_to", 901L)
                .containsEntry("priority", "HIGH");
        assertThat(text(call(client("priya@example.com"), "list_tickets", Map.of())))
                .contains("Kitchen sink leaking");
    }

    @Test
    void shouldHideOtherResidentsTicketsAndForbidStaffFromCreating() {
        var id = createAsResident();

        var other = call(client("arjun@example.com"), "get_ticket", Map.of("ticketId", id));
        assertThat(other.isError()).isTrue();
        assertThat(text(other)).contains("not found");
        assertThat(text(call(client("arjun@example.com"), "list_tickets", Map.of())))
                .doesNotContain("Kitchen sink leaking");

        var staffCreate = call(
                client("ravi@example.com"),
                "create_ticket",
                Map.of("title", "x", "description", "x", "priority", "LOW", "categoryId", 1));
        assertThat(staffCreate.isError()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from tickets", Long.class))
                .isEqualTo(1);
    }

    @Test
    void shouldLetAssignedStaffMoveTheTicketAndComment() {
        var id = createAsResident();
        var staff = client("ravi@example.com");

        assertThat(text(call(staff, "list_tickets", Map.of("status", "OPEN")))).contains("Kitchen sink leaking");
        var moved = call(staff, "change_ticket_status", Map.of("ticketId", id, "status", "IN_PROGRESS"));
        assertThat(moved.isError()).as(text(moved)).isNotEqualTo(Boolean.TRUE);
        call(staff, "add_comment", Map.of("ticketId", id, "content", "On my way."));

        assertThat(jdbcTemplate.queryForObject("select status from tickets where id = ?", String.class, id))
                .isEqualTo("IN_PROGRESS");
        assertThat(text(call(client("priya@example.com"), "get_ticket", Map.of("ticketId", id))))
                .contains("On my way.")
                .contains("IN_PROGRESS");

        var residentMove =
                call(client("priya@example.com"), "change_ticket_status", Map.of("ticketId", id, "status", "RESOLVED"));
        assertThat(residentMove.isError()).isTrue();
    }

    @Test
    void shouldLetOnlyAdministratorsAssignTickets() {
        var id = createAsResident();

        assertThat(call(client("priya@example.com"), "assign_ticket", Map.of("ticketId", id, "staffId", 901))
                        .isError())
                .isTrue();
        var admin = call(client("admin@gmail.com"), "assign_ticket", Map.of("ticketId", id, "staffId", 901));
        assertThat(admin.isError()).as(text(admin)).isNotEqualTo(Boolean.TRUE);
        assertThat(text(call(client("admin@gmail.com"), "list_tickets", Map.of())))
                .contains("Kitchen sink leaking");
    }
}
