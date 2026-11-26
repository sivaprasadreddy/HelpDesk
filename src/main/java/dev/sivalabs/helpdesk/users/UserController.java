package dev.sivalabs.helpdesk.users;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.categories.CategoryDto;
import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import jakarta.validation.Valid;
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
 * ADMIN-only pages (secured by the {@code /admin/**} rule in WebSecurityConfig) to list and create residents ({@code /admin/residents}) and staff ({@code /admin/staff}).
 */
@Controller
@RequestMapping("/admin/{userType:residents|staff}")
class UserController {
    private final UserService userService;
    private final CategoryAPI categoryService;

    UserController(UserService userService, CategoryAPI categoryService) {
        this.userService = userService;
        this.categoryService = categoryService;
    }

    @GetMapping
    String list(@PathVariable String userType, Model model) {
        var type = UserType.of(userType);
        model.addAttribute("type", type);
        model.addAttribute("users", userService.findUsersByRole(type.role()));
        if (type.role() == Role.ROLE_STAFF) {
            Map<Long, String> categoryNames = categoryService.findAllCategories().stream()
                    .collect(Collectors.toMap(CategoryDto::id, CategoryDto::name));
            model.addAttribute("categoryNames", categoryNames);
        }
        return "users/list";
    }

    @GetMapping("/new")
    String newUserForm(@PathVariable String userType, Model model) {
        model.addAttribute("type", UserType.of(userType));
        model.addAttribute("user", CreateUserForm.empty());
        return "users/create";
    }

    @PostMapping
    String createUser(
            @PathVariable String userType,
            @Valid @ModelAttribute("user") CreateUserForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        var type = UserType.of(userType);
        if (!bindingResult.hasErrors()) {
            try {
                userService.createUser(new CreateUserCmd(form.name(), form.email(), form.password(), type.role()));
                redirectAttributes.addFlashAttribute("successMessage", type.singular() + " created successfully");
                return "redirect:/admin/" + type.path();
            } catch (EmailAlreadyExistsException e) {
                bindingResult.rejectValue("email", "email.exists", "A user with this email already exists");
            }
        }
        model.addAttribute("type", type);
        return "users/create";
    }

    record UserType(String path, Role role, String title, String singular) {
        static UserType of(String path) {
            return switch (path) {
                case "residents" -> new UserType(path, Role.ROLE_USER, "Residents", "Resident");
                case "staff" -> new UserType(path, Role.ROLE_STAFF, "Staff", "Staff member");
                default -> throw new ResourceNotFoundException("Unknown user type: " + path);
            };
        }
    }
}
