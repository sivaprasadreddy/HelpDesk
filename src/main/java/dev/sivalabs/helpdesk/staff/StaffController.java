package dev.sivalabs.helpdesk.staff;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.categories.CategoryDto;
import dev.sivalabs.helpdesk.users.EmailAlreadyExistsException;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ADMIN-only (secured by the {@code /admin/**} rule in WebSecurityConfig): list, create, update, deactivate and activate
 * staff.
 */
@Controller
@RequestMapping("/admin/staff")
class StaffController {
    private static final String REDIRECT_LIST = "redirect:/admin/staff";
    private final StaffService staffService;
    private final CategoryAPI categoryService;

    StaffController(StaffService staffService, CategoryAPI categoryService) {
        this.staffService = staffService;
        this.categoryService = categoryService;
    }

    @GetMapping
    String list(Model model) {
        Map<Long, String> categoryNames = categoryService.findAllCategories().stream()
                .collect(Collectors.toMap(CategoryDto::id, CategoryDto::name));
        model.addAttribute("staffMembers", staffService.findStaffMembers());
        model.addAttribute("categoryNames", categoryNames);
        return "staff/list";
    }

    @GetMapping("/new")
    String newStaffForm(Model model) {
        model.addAttribute("staff", CreateStaffForm.empty());
        return "staff/create";
    }

    @PostMapping
    String create(
            @Valid @ModelAttribute("staff") CreateStaffForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                staffService.createStaffMember(new CreateStaffCmd(
                        form.name(), form.email(), form.password(), form.mobile(), form.permanentAddress()));
                redirectAttributes.addFlashAttribute("successMessage", "Staff member created successfully");
                return REDIRECT_LIST;
            } catch (EmailAlreadyExistsException e) {
                bindingResult.rejectValue("email", "email.exists", "A user with this email already exists");
            }
        }
        return "staff/create";
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        var staff = staffService.getStaffMember(id);
        model.addAttribute("staff", UpdateStaffForm.from(staff));
        populate(model, id, staff.categoryIds());
        return "staff/edit";
    }

    @PostMapping("/{id}")
    String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("staff") UpdateStaffForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        var current = staffService.getStaffMember(id);
        if (!bindingResult.hasErrors()) {
            try {
                staffService.updateStaffMember(new UpdateStaffCmd(
                        id, form.name(), form.email(), form.mobile(), form.permanentAddress(), form.categoryIds()));
                redirectAttributes.addFlashAttribute("successMessage", "Staff member updated successfully");
                return REDIRECT_LIST;
            } catch (EmailAlreadyExistsException e) {
                bindingResult.rejectValue("email", "email.exists", "A user with this email already exists");
            } catch (InvalidCategoryException e) {
                bindingResult.rejectValue("categoryIds", "categories.invalid", "Choose only active categories");
            }
        }
        populate(model, id, current.categoryIds());
        return "staff/edit";
    }

    @PostMapping("/{id}/deactivate")
    String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        staffService.setStaffActive(id, false);
        redirectAttributes.addFlashAttribute(
                "successMessage", "Staff member deactivated; their open tickets were reassigned");
        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/activate")
    String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        staffService.setStaffActive(id, true);
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
