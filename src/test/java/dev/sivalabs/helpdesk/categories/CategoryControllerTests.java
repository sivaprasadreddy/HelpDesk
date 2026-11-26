package dev.sivalabs.helpdesk.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import dev.sivalabs.helpdesk.BaseIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;

@Sql("/test-data.sql")
@WithMockUser(roles = "ADMIN")
class CategoryControllerTests extends BaseIT {
    @Autowired
    CategoryService categoryService;

    @Test
    void shouldListCategories() {
        var result = mvc.get().uri("/admin/categories");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("Plumbing").contains("Gardening").contains("Deactivate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/categories/new", "/admin/categories/1/edit"})
    void shouldRenderForms(String uri) {
        assertThat(mvc.get().uri(uri)).hasStatusOk();
    }

    @Test
    void shouldReturn404ForUnknownCategory() {
        assertThat(mvc.get().uri("/admin/categories/9999/edit")).hasStatus(404);
    }

    @Test
    void shouldCreateCategory() {
        var result = mvc.post()
                .uri("/admin/categories")
                .with(csrf())
                .param("name", "  Pest Control ")
                .param("description", "Termites and rodents");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/admin/categories");
        assertThat(categoryService.findActiveCategories())
                .anySatisfy(c -> assertThat(c.name()).isEqualTo("Pest Control"));
    }

    @Test
    void shouldRejectInvalidAndDuplicateNames() {
        assertThat(mvc.post()
                        .uri("/admin/categories")
                        .with(csrf())
                        .param("name", "")
                        .param("description", ""))
                .hasStatusOk()
                .bodyText()
                .contains("Name is required");

        assertThat(mvc.post().uri("/admin/categories").with(csrf()).param("name", "plumbing"))
                .hasStatusOk()
                .bodyText()
                .contains("A category with this name already exists");
    }

    @Test
    void shouldUpdateCategory() {
        var result = mvc.post()
                .uri("/admin/categories/1")
                .with(csrf())
                .param("name", "Plumbing & Water")
                .param("description", "");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/admin/categories");
        var updated = categoryService.getCategory(1L);
        assertThat(updated.name()).isEqualTo("Plumbing & Water");
        assertThat(updated.description()).isNull();
    }

    @Test
    void shouldAllowKeepingOwnNameButNotAnothersOnUpdate() {
        assertThat(mvc.post().uri("/admin/categories/1").with(csrf()).param("name", "PLUMBING"))
                .hasStatus3xxRedirection();
        assertThat(mvc.post().uri("/admin/categories/1").with(csrf()).param("name", "Electrical"))
                .hasStatusOk()
                .bodyText()
                .contains("A category with this name already exists");
    }

    @Test
    void shouldDeactivateAndReactivateCategory() {
        assertThat(mvc.post().uri("/admin/categories/2/deactivate").with(csrf()))
                .hasStatus3xxRedirection();
        assertThat(categoryService.getCategory(2L).active()).isFalse();
        assertThat(categoryService.findActiveCategories()).noneMatch(c -> c.id().equals(2L));
        assertThat(categoryService.findAllCategories()).anyMatch(c -> c.id().equals(2L));

        assertThat(mvc.post().uri("/admin/categories/2/activate").with(csrf())).hasStatus3xxRedirection();
        assertThat(categoryService.getCategory(2L).active()).isTrue();
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void shouldForbidNonAdmins() {
        assertThat(mvc.get().uri("/admin/categories")).hasStatus(403);
        assertThat(mvc.post().uri("/admin/categories/2/deactivate").with(csrf()))
                .hasStatus(403);
    }
}
