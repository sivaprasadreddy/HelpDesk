package dev.sivalabs.helpdesk.users;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.categories.CategoryDto;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** ADMIN-only (secured by the {@code /admin/**} rule in WebSecurityConfig): update, deactivate and activate staff. */
@Controller
@RequestMapping("/admin/staff/{id}")
class StaffController {
    private static final String REDIRECT_LIST = "redirect:/admin/staff";
    private final UserService userService;
    private final CategoryAPI categoryService;

    StaffController(UserService userService, CategoryAPI categoryService) {
        this.userService = userService;
        this.categoryService = categoryService;
    }

    @GetMapping("/edit")
    String editForm(@PathVariable Long id, Model model) {
        var staff = userService.getStaffMember(id);
        model.addAttribute("staff", UpdateStaffForm.from(staff));
        populate(model, id, staff.categoryIds());
        return "users/edit-staff";
    }

    @PostMapping
    String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("staff") UpdateStaffForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        var current = userService.getStaffMember(id);
        if (!bindingResult.hasErrors()) {
            try {
                userService.updateStaffMember(new UpdateStaffCmd(id, form.name(), form.email(), form.categoryIds()));
                redirectAttributes.addFlashAttribute("successMessage", "Staff member updated successfully");
                return REDIRECT_LIST;
            } catch (EmailAlreadyExistsException e) {
                bindingResult.rejectValue("email", "email.exists", "A user with this email already exists");
            } catch (InvalidCategoryException e) {
                bindingResult.rejectValue("categoryIds", "categories.invalid", "Choose only active categories");
            }
        }
        populate(model, id, current.categoryIds());
        return "users/edit-staff";
    }

    @PostMapping("/deactivate")
    String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.setStaffActive(id, false);
        redirectAttributes.addFlashAttribute(
                "successMessage", "Staff member deactivated; their open tickets were reassigned");
        return REDIRECT_LIST;
    }

    @PostMapping("/activate")
    String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.setStaffActive(id, true);
        redirectAttributes.addFlashAttribute("successMessage", "Staff member activated");
        return REDIRECT_LIST;
    }

    /** Active categories, plus inactive ones the staff member already handles so they are not dropped silently. */
    private void populate(Model model, Long id, Collection<Long> assigned) {
        List<CategoryDto> categories = categoryService.findAllCategories().stream()
                .filter(c -> c.active() || assigned.contains(c.id()))
                .toList();
        model.addAttribute("staffId", id);
        model.addAttribute("categories", categories);
    }
}
