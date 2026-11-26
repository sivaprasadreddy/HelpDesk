package dev.sivalabs.helpdesk.categories;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** ADMIN-only (secured by the {@code /admin/**} rule in WebSecurityConfig). */
@Controller
@RequestMapping("/admin/categories")
class CategoryController {
    private static final String REDIRECT_LIST = "redirect:/admin/categories";
    private final CategoryService categoryService;

    CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("categories", categoryService.findAllCategories());
        return "categories/list";
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute("category", CategoryForm.empty());
        return "categories/form";
    }

    @PostMapping
    String create(
            @Valid @ModelAttribute("category") CategoryForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                categoryService.createCategory(form.name(), form.description());
                redirectAttributes.addFlashAttribute("successMessage", "Category created successfully");
                return REDIRECT_LIST;
            } catch (CategoryNameAlreadyExistsException e) {
                rejectDuplicate(bindingResult);
            }
        }
        return "categories/form";
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("categoryId", id);
        model.addAttribute("category", CategoryForm.from(categoryService.getCategory(id)));
        return "categories/form";
    }

    @PostMapping("/{id}")
    String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("category") CategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                categoryService.updateCategory(id, form.name(), form.description());
                redirectAttributes.addFlashAttribute("successMessage", "Category updated successfully");
                return REDIRECT_LIST;
            } catch (CategoryNameAlreadyExistsException e) {
                rejectDuplicate(bindingResult);
            }
        }
        model.addAttribute("categoryId", id);
        return "categories/form";
    }

    @PostMapping("/{id}/deactivate")
    String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        categoryService.setActive(id, false);
        redirectAttributes.addFlashAttribute("successMessage", "Category deactivated");
        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/activate")
    String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        categoryService.setActive(id, true);
        redirectAttributes.addFlashAttribute("successMessage", "Category activated");
        return REDIRECT_LIST;
    }

    private static void rejectDuplicate(BindingResult bindingResult) {
        bindingResult.rejectValue("name", "name.exists", "A category with this name already exists");
    }
}
