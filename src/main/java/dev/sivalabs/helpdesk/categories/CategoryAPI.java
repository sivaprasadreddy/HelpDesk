package dev.sivalabs.helpdesk.categories;

import java.util.List;
import org.springframework.stereotype.Component;

/** The categories module's facade for other modules. */
@Component
public class CategoryAPI {
    private final CategoryService categoryService;

    CategoryAPI(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public List<CategoryDto> findAllCategories() {
        return categoryService.findAllCategories();
    }

    public List<CategoryDto> findActiveCategories() {
        return categoryService.findActiveCategories();
    }

    /** Whether a category with this id exists and is active (new tickets may only use active categories). */
    public boolean isActiveCategory(Long id) {
        return categoryService.isActiveCategory(id);
    }
}
