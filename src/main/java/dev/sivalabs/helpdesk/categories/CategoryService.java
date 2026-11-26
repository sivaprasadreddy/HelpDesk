package dev.sivalabs.helpdesk.categories;

import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findAllCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryService::toDto)
                .toList();
    }

    /** Categories that new tickets may use and staff may be assigned to. */
    @Transactional(readOnly = true)
    public List<CategoryDto> findActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(CategoryService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isActiveCategory(Long id) {
        return categoryRepository.existsByIdAndActiveTrue(id);
    }

    @Transactional(readOnly = true)
    public CategoryDto getCategory(Long id) {
        return toDto(load(id));
    }

    @Transactional
    public void createCategory(String name, String description) {
        var trimmed = name.trim();
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw new CategoryNameAlreadyExistsException(trimmed);
        }
        var category = new CategoryEntity();
        category.setName(trimmed);
        category.setDescription(blankToNull(description));
        categoryRepository.save(category);
    }

    @Transactional
    public void updateCategory(Long id, String name, String description) {
        var category = load(id);
        var trimmed = name.trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new CategoryNameAlreadyExistsException(trimmed);
        }
        category.setName(trimmed);
        category.setDescription(blankToNull(description));
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        load(id).setActive(active);
    }

    private CategoryEntity load(Long id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static CategoryDto toDto(CategoryEntity e) {
        return new CategoryDto(e.getId(), e.getName(), e.getDescription(), e.isActive());
    }
}
