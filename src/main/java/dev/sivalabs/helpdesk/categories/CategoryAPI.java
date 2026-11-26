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
}
