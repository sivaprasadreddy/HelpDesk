package dev.sivalabs.helpdesk.users;

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
    UserRepository userRepository;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    /** The pool does not auto-commit, so fixture writes need a transaction. */
    void sql(String statement, Object... args) {
        tx.executeWithoutResult(s -> jdbcTemplate.update(statement, args));
    }

    Long createStaff(String email) {
        userService.createUser(new CreateUserCmd("Bob Staff", email, "secret123", Role.ROLE_STAFF));
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
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
                .param("categoryIds", "1", "2");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/admin/staff");
        tx.executeWithoutResult(s -> {
            var user = userRepository.findById(id).orElseThrow();
            assertThat(user.getName()).isEqualTo("Bob Plumber");
            assertThat(user.getEmail()).isEqualTo("bob.p@example.com");
            assertThat(user.getCategoryIds()).containsExactlyInAnyOrder(1L, 2L);
        });
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
                        .param("email", "bob@example.com"))
                .bodyText()
                .contains("Name is required");
        assertThat(mvc.post()
                        .uri("/admin/staff/{id}", id)
                        .with(csrf())
                        .param("name", "Bob")
                        .param("email", "admin@gmail.com"))
                .bodyText()
                .contains("A user with this email already exists");
    }

    @Test
    void shouldDeactivateAndActivateStaff() {
        var id = createStaff("bob@example.com");

        assertThat(mvc.post().uri("/admin/staff/{id}/deactivate", id).with(csrf()))
                .hasRedirectedUrl("/admin/staff");
        assertThat(userRepository.findById(id).orElseThrow().isActive()).isFalse();
        assertThat(mvc.get().uri("/admin/staff"))
                .bodyText()
                .contains("Inactive")
                .contains("Activate");

        mvc.post().uri("/admin/staff/{id}/activate", id).with(csrf()).exchange();
        assertThat(userRepository.findById(id).orElseThrow().isActive()).isTrue();
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
