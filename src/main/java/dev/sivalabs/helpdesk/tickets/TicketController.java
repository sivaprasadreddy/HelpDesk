package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.AuthUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Creating tickets: ADMIN and USER only (see {@link AccessPolicy#canCreateTicket}). */
@Controller
@RequestMapping("/tickets")
class TicketController {
    private static final String CREATE_VIEW = "tickets/create";
    private final TicketService ticketService;
    private final CategoryAPI categoryAPI;
    private final AccessPolicy accessPolicy;

    TicketController(TicketService ticketService, CategoryAPI categoryAPI, AccessPolicy accessPolicy) {
        this.ticketService = ticketService;
        this.categoryAPI = categoryAPI;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/new")
    String newTicketForm(Authentication authentication, Model model) {
        requireCanCreate(authentication);
        model.addAttribute("ticket", CreateTicketForm.empty());
        addFormOptions(model);
        return CREATE_VIEW;
    }

    @PostMapping
    String createTicket(
            Authentication authentication,
            @Valid @ModelAttribute("ticket") CreateTicketForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        requireCanCreate(authentication);
        if (!bindingResult.hasErrors()) {
            try {
                var cmd = new CreateTicketCmd(
                        form.title(),
                        form.description(),
                        form.priority(),
                        form.categoryId(),
                        AuthUtils.getCurrentUserIdOrThrow());
                var id = ticketService.createTicket(cmd);
                redirectAttributes.addFlashAttribute("successMessage", "Ticket #" + id + " created successfully");
                return "redirect:/home";
            } catch (InvalidTicketCategoryException e) {
                bindingResult.rejectValue("categoryId", "category.invalid", "Choose an active category");
            }
        }
        addFormOptions(model);
        return CREATE_VIEW;
    }

    private void requireCanCreate(Authentication authentication) {
        if (!accessPolicy.canCreateTicket(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only administrators and residents can create tickets");
        }
    }

    private void addFormOptions(Model model) {
        model.addAttribute("priorities", TicketPriority.values());
        model.addAttribute("categories", categoryAPI.findActiveCategories());
    }
}
