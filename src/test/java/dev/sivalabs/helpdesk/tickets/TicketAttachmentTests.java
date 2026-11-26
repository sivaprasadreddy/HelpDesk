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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

@Sql("/test-data.sql")
class TicketAttachmentTests extends BaseIT {
    static final SecurityUser RESIDENT = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser OTHER = new SecurityUser(902L, "Arjun Reddy", "arjun@example.com", "x", Role.ROLE_USER);

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> jdbcTemplate.update(
                "insert into users(id, email, password, name, role) values (900, 'priya@example.com', 'x',"
                        + " 'Priya Nair', 'ROLE_USER'), (902, 'arjun@example.com', 'x', 'Arjun Reddy', 'ROLE_USER')"));
    }

    static MockMultipartFile image(String name, String type, byte[] data) {
        return new MockMultipartFile("images", name, type, data);
    }

    MvcTestResult create(MockMultipartFile... files) {
        var request =
                mvc.post().multipart().uri("/tickets").with(user(RESIDENT)).with(csrf());
        for (var file : files) {
            request = request.file(file);
        }
        return request.param("title", "Broken tap")
                .param("description", "The bathroom tap is broken.")
                .param("priority", "HIGH")
                .param("categoryId", "1")
                .exchange();
    }

    Long onlyTicketId() {
        return jdbcTemplate.queryForObject("select id from tickets", Long.class);
    }

    long attachmentCount() {
        return jdbcTemplate.queryForObject("select count(*) from ticket_attachments", Long.class);
    }

    @Test
    void shouldShowTheImagePickerOnTheCreateForm() {
        assertThat(mvc.get().uri("/tickets/new").with(user(RESIDENT)))
                .bodyText()
                .contains("multipart/form-data")
                .contains("Up to 3 images");
    }

    @Test
    void shouldStoreImagesAndShowThemOnTheDetailsPage() {
        assertThat(create(image("tap.png", "image/png", PNG), image("../../sink.jpg", "image/png", JPEG)))
                .hasStatus3xxRedirection();

        var ticketId = onlyTicketId();
        var rows = jdbcTemplate.queryForList("select id, file_name, content_type from ticket_attachments order by id");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("file_name", "tap.png").containsEntry("content_type", "image/png");
        // The type comes from the bytes, and the path is dropped from the name.
        assertThat(rows.get(1)).containsEntry("file_name", "sink.jpg").containsEntry("content_type", "image/jpeg");

        var firstId = rows.get(0).get("id");
        assertThat(mvc.get().uri("/tickets/{id}", ticketId).with(user(RESIDENT)))
                .bodyText()
                .contains("Images (2)")
                .contains("tap.png")
                .contains("/tickets/" + ticketId + "/attachments/" + firstId);

        var download = mvc.get()
                .uri("/tickets/{id}/attachments/{aid}", ticketId, firstId)
                .with(user(RESIDENT))
                .exchange();
        assertThat(download).hasStatusOk().hasContentType("image/png");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PNG);
    }

    @Test
    void shouldCreateTicketWithoutImages() {
        assertThat(create(image("", "application/octet-stream", new byte[0]))).hasStatus3xxRedirection();
        assertThat(attachmentCount()).isZero();
        assertThat(mvc.get().uri("/tickets/{id}", onlyTicketId()).with(user(RESIDENT)))
                .bodyText()
                .doesNotContain("Images (");
    }

    @Test
    void shouldHideImagesFromUsersWhoCannotViewTheTicket() {
        create(image("tap.png", "image/png", PNG));
        var ticketId = onlyTicketId();
        var attachmentId = jdbcTemplate.queryForObject("select id from ticket_attachments", Long.class);

        assertThat(mvc.get()
                        .uri("/tickets/{id}/attachments/{aid}", ticketId, attachmentId)
                        .with(user(OTHER)))
                .hasStatus(404);
        assertThat(mvc.get()
                        .uri("/tickets/{id}/attachments/{aid}", ticketId, 9999)
                        .with(user(RESIDENT)))
                .hasStatus(404);
    }

    @Test
    void shouldRejectMoreThanThreeImages() {
        var result = create(
                image("1.png", "image/png", PNG),
                image("2.png", "image/png", PNG),
                image("3.png", "image/png", PNG),
                image("4.png", "image/png", PNG));

        assertThat(result).hasStatusOk().bodyText().contains("Attach at most 3 images");
        assertThat(jdbcTemplate.queryForObject("select count(*) from tickets", Long.class))
                .isZero();
    }

    @Test
    void shouldRejectFilesThatAreNotImages() {
        var result = create(image("evil.svg", "image/png", "<svg onload=alert(1)>".getBytes()));

        assertThat(result).hasStatusOk().bodyText().contains("evil.svg is not a PNG, JPEG, GIF or WebP image");
        assertThat(attachmentCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from tickets", Long.class))
                .isZero();
    }
}
