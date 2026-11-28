package dev.sivalabs.helpdesk.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@Sql("/test-data.sql")
@WithMockUser(roles = "ADMIN")
class StaffControllerTests extends BaseIT {
    @Autowired
    StaffService staffService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    /** The pool does not auto-commit, so fixture writes need a transaction. */
    void sql(String statement, Object... args) {
        tx.executeWithoutResult(s -> jdbcTemplate.update(statement, args));
    }

    Long createStaff(String email) {
        staffService.createStaffMember(new CreateStaffCmd("Bob Staff", email, "secret123", "9876543210", null));
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/staff", "/admin/staff/new"})
    void shouldRenderStaffPages(String uri) {
        assertThat(mvc.get().uri(uri)).hasStatusOk();
    }

    @Test
    void shouldCreateStaffMember() {
        var result = mvc.post()
                .uri("/admin/staff")
                .with(csrf())
                .param("name", "Bob Plumber")
                .param("email", "Bob@Example.com")
                .param("password", "secret123")
                .param("flatNumber", "Z-999")
                .param("mobile", "+919876543210")
                .param("permanentAddress", "");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/admin/staff");
        var user = jdbcTemplate.queryForMap("select id, role from users where email = ?", "bob@example.com");
        assertThat(user).containsEntry("role", "ROLE_STAFF");
        assertThat(jdbcTemplate.queryForMap(
                        "select mobile, permanent_address from staff_profiles where user_id = ?", user.get("id")))
                .containsEntry("mobile", "+919876543210")
                .containsEntry("permanent_address", null);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from resident_profiles where user_id = ?", Integer.class, user.get("id")))
                .isZero();
        assertThat(mvc.get().uri("/admin/staff")).bodyText().contains("Bob Plumber");
        assertThat(mvc.get().uri("/admin/residents")).bodyText().doesNotContain("Bob Plumber");
    }

    @Test
    void shouldShowCreateValidationErrors() {
        var result = mvc.post()
                .uri("/admin/staff")
                .with(csrf())
                .param("name", "")
                .param("email", "not-an-email")
                .param("password", "short");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Name is required")
                .contains("Enter a valid email address")
                .contains("Password must be at least 8 characters");
    }

    @Test
    void shouldRenderEditFormWithCategoryCombobox() {
        var id = createStaff("bob@example.com");
        var result = mvc.get().uri("/admin/staff/{id}/edit", id);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("multiple").contains("Plumbing").contains("Gardening");
    }

    @Test
    void shouldReturn404ForNonStaffUser() {
        assertThat(mvc.get().uri("/admin/staff/1/edit")).hasStatus(404);
        assertThat(mvc.get().uri("/admin/staff/9999/edit")).hasStatus(404);
    }

    @Test
    void shouldUpdateStaffAndAssignCategories() {
        var id = createStaff("bob@example.com");
        var result = mvc.post()
                .uri("/admin/staff/{id}", id)
                .with(csrf())
                .param("name", "Bob Plumber")
                .param("email", "Bob.P@Example.com")
                .param("mobile", "9123456780")
                .param("permanentAddress", "12 Gandhi Street, Chennai")
                .param("categoryIds", "1", "2");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/admin/staff");
        var staff = staffService.getStaffMember(id);
        assertThat(staff.name()).isEqualTo("Bob Plumber");
        assertThat(staff.email()).isEqualTo("bob.p@example.com");
        assertThat(staff.categoryIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(jdbcTemplate.queryForMap(
                        "select mobile, permanent_address from staff_profiles where user_id = ?", id))
                .containsEntry("mobile", "9123456780")
                .containsEntry("permanent_address", "12 Gandhi Street, Chennai");
        assertThat(mvc.get().uri("/admin/staff"))
                .bodyText()
                .contains("Bob Plumber")
                .contains("Plumbing")
                .contains("Electrical");
    }

    @Test
    void shouldClearCategoriesWhenNoneSelected() {
        var id = createStaff("bob@example.com");
        sql("insert into staff_categories(user_id, category_id) values (?, 1)", id);

        mvc.post()
                .uri("/admin/staff/{id}", id)
                .with(csrf())
                .param("name", "Bob")
                .param("email", "bob@example.com")
                .param("mobile", "9876543210")
                .exchange();

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from staff_categories where user_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void shouldRejectInactiveCategoryNotAlreadyAssigned() {
        var id = createStaff("bob@example.com");
        sql("update categories set active = false where id = 3");

        var result = mvc.post()
                .uri("/admin/staff/{id}", id)
                .with(csrf())
                .param("name", "Bob")
                .param("email", "bob@example.com")
                .param("mobile", "9876543210")
                .param("categoryIds", "3");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("Choose only active categories");
    }

    @Test
    void shouldShowValidationAndDuplicateEmailErrors() {
        var id = createStaff("bob@example.com");
        assertThat(mvc.post()
                        .uri("/admin/staff/{id}", id)
                        .with(csrf())
                        .param("name", "")
                        .param("email", "bob@example.com")
                        .param("mobile", "9876543210"))
                .bodyText()
                .contains("Name is required");
        assertThat(mvc.post()
                        .uri("/admin/staff/{id}", id)
                        .with(csrf())
                        .param("name", "Bob")
                        .param("email", "admin@gmail.com")
                        .param("mobile", "9876543210"))
                .bodyText()
                .contains("A user with this email already exists");
    }

    @Test
    void shouldDeactivateAndActivateStaff() {
        var id = createStaff("bob@example.com");

        assertThat(mvc.post().uri("/admin/staff/{id}/deactivate", id).with(csrf()))
                .hasRedirectedUrl("/admin/staff");
        assertThat(staffService.getStaffMember(id).active()).isFalse();
        assertThat(mvc.get().uri("/admin/staff"))
                .bodyText()
                .contains("Inactive")
                .contains("Activate");

        mvc.post().uri("/admin/staff/{id}/activate", id).with(csrf()).exchange();
        assertThat(staffService.getStaffMember(id).active()).isTrue();
    }

    @Test
    void deactivatedStaffCannotLogIn() {
        sql(
                "insert into users(id, email, password, name, role, active) values (nextval('user_id_seq'), 'off@example.com', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'Off', 'ROLE_STAFF', false)");

        var result = mvc.post()
                .uri("/login")
                .with(csrf())
                .param("username", "off@example.com")
                .param("password", "admin");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/login?error");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "STAFF"})
    void shouldForbidNonAdmins(String role) {
        var id = createStaff("bob@example.com");
        assertThat(mvc.post()
                        .uri("/admin/staff/{id}/deactivate", id)
                        .with(csrf())
                        .with(user("u").roles(role)))
                .hasStatus(403);
    }
}
