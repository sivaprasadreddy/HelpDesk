package dev.sivalabs.helpdesk.residents;

import dev.sivalabs.helpdesk.users.EmailAlreadyExistsException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** ADMIN-only (secured by the {@code /admin/**} rule in WebSecurityConfig): list and create residents. */
@Controller
@RequestMapping("/admin/residents")
class ResidentController {
    private final ResidentService residentService;

    ResidentController(ResidentService residentService) {
        this.residentService = residentService;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("residents", residentService.findResidents());
        return "residents/list";
    }

    @GetMapping("/new")
    String newResidentForm(Model model) {
        model.addAttribute("resident", CreateResidentForm.empty());
        return "residents/create";
    }

    @PostMapping
    String create(
            @Valid @ModelAttribute("resident") CreateResidentForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                residentService.createResident(new CreateResidentCmd(
                        form.name(),
                        form.email(),
                        form.password(),
                        form.flatNumber(),
                        form.mobile(),
                        form.permanentAddress()));
                redirectAttributes.addFlashAttribute("successMessage", "Resident created successfully");
                return "redirect:/admin/residents";
            } catch (EmailAlreadyExistsException e) {
                bindingResult.rejectValue("email", "email.exists", "A user with this email already exists");
            }
        }
        return "residents/create";
    }
}
