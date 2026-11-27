package dev.sivalabs.helpdesk.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Ticket changes reach the open pages of the people who may view the ticket, and nobody else. */
@Sql("/test-data.sql")
class LiveUpdatesTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser MEERA = new SecurityUser(902L, "Meera Iyer", "meera@example.com", "x", Role.ROLE_USER);
    static final SecurityUser ANITA = new SecurityUser(903L, "Anita Sharma", "anita@example.com", "x", Role.ROLE_STAFF);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LiveUpdateHub hub;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    Recorder admin, priya, ravi, meera, anita;

    static final class Recorder implements LiveUpdateHub.Subscriber {
        final List<LiveTicketChange> received = new CopyOnWriteArrayList<>();

        @Override
        public void send(LiveTicketChange change) {
            received.add(change);
        }

        @Override
        public void heartbeat() {}

        @Override
        public void close() {}

        List<String> kinds(long ticketId) {
            return received.stream()
                    .filter(c -> c.ticketId() == ticketId)
                    .map(LiveTicketChange::kind)
                    .toList();
        }
    }

    void sql(String statement, Object... args) {
        tx.executeWithoutResult(s -> jdbcTemplate.update(statement, args));
    }

    @BeforeEach
    void setUp() {
        hub.closeAll();
        sql("""
                insert into users(id, email, password, name, role) values
                (900, 'priya@example.com', 'x', 'Priya Nair', 'ROLE_USER'),
                (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF'),
                (902, 'meera@example.com', 'x', 'Meera Iyer', 'ROLE_USER'),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF')
                """);
        sql("insert into staff_categories(user_id, category_id) values (901, 2), (903, 2)");
        sql("""
                insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)
                values (1, 'Power outage', 'No power', 'HIGH', 'OPEN', 2, 900, 901)
                """);
        admin = subscribe(ADMIN);
        priya = subscribe(PRIYA);
        ravi = subscribe(RAVI);
        meera = subscribe(MEERA);
        anita = subscribe(ANITA);
    }

    @AfterEach
    void tearDown() {
        hub.closeAll();
    }

    Recorder subscribe(SecurityUser user) {
        var recorder = new Recorder();
        hub.add(recorder, user);
        return recorder;
    }

    @Test
    void streamRequiresSignIn() throws Exception {
        mockMvc.perform(get("/live/tickets").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserGetsAnEventStream() throws Exception {
        hub.closeAll();
        mockMvc.perform(get("/live/tickets").accept(MediaType.TEXT_EVENT_STREAM).with(user(PRIYA)))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE));
        assertThat(hub.size()).isEqualTo(1);
    }

    @Test
    void statusChangeReachesRaiserAssigneeAndAdminOnly() {
        mvc.post()
                .uri("/tickets/1/status")
                .with(user(RAVI))
                .with(csrf())
                .param("from", "OPEN")
                .param("to", "IN_PROGRESS")
                .exchange();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(priya.kinds(1)).containsExactly("status-changed");
            assertThat(ravi.kinds(1)).containsExactly("status-changed");
            assertThat(admin.kinds(1)).containsExactly("status-changed");
        });
        assertThat(meera.received).isEmpty();
        assertThat(anita.received).isEmpty();
    }

    @Test
    void commentReachesTheOtherParty() {
        mvc.post()
                .uri("/tickets/1/comments")
                .with(user(PRIYA))
                .with(csrf())
                .param("content", "Any update?")
                .exchange();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(ravi.kinds(1)).containsExactly("comment-added"));
        assertThat(anita.received).isEmpty();
    }

    @Test
    void reassignmentReachesThePreviousAndTheNewAssignee() {
        mvc.post()
                .uri("/tickets/1/assignee")
                .with(user(ADMIN))
                .with(csrf())
                .param("staffId", "903")
                .exchange();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(anita.kinds(1)).containsExactly("assigned");
            assertThat(ravi.kinds(1)).containsExactly("assigned");
            assertThat(priya.kinds(1)).containsExactly("assigned");
        });
        assertThat(meera.received).isEmpty();
    }

    @Test
    void aBrokenConnectionIsDropped() {
        var broken = new LiveUpdateHub.Subscriber() {
            @Override
            public void send(LiveTicketChange change) throws java.io.IOException {
                throw new java.io.IOException("gone");
            }

            @Override
            public void heartbeat() throws java.io.IOException {
                throw new java.io.IOException("gone");
            }

            @Override
            public void close() {}
        };
        hub.add(broken, ADMIN);
        var before = hub.size();

        hub.broadcast(new LiveTicketChange(1L, "updated", 900L, 901L, null));

        assertThat(hub.size()).isEqualTo(before - 1);
        assertThat(admin.kinds(1)).containsExactly("updated");
    }
}
