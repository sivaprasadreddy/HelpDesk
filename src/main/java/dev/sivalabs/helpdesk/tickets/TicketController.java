package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.AuthUtils;
import jakarta.validation.Valid;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Creating tickets (ADMIN and USER, see {@link AccessPolicy#canCreateTicket}), the resident's own ticket list
 * ({@link AccessPolicy#canViewOwnTickets}) and the staff member's assigned list
 * ({@link AccessPolicy#canViewAssignedTickets}).
 */
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
                return accessPolicy.canViewOwnTickets(authentication.getPrincipal())
                        ? "redirect:/tickets/mine"
                        : "redirect:/home";
            } catch (InvalidTicketCategoryException e) {
                bindingResult.rejectValue("categoryId", "category.invalid", "Choose an active category");
            }
        }
        addFormOptions(model);
        return CREATE_VIEW;
    }

    /** REQ-014: the resident's own tickets, optionally of one status ({@code ?status=OPEN}). */
    @GetMapping("/mine")
    String myTickets(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        if (!accessPolicy.canViewOwnTickets(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only residents have a list of their own tickets");
        }
        model.addAttribute("pageTitle", "My Tickets");
        model.addAttribute("listUrl", "/tickets/mine");
        model.addAttribute("showCreator", false);
        model.addAttribute("canCreate", true);
        model.addAttribute("emptyTitle", "No tickets yet");
        model.addAttribute("emptyDescription", "Report an issue and follow its progress here.");
        return renderList(TicketScope.RAISED_BY, status, pageable, model);
    }

    /** REQ-015: the tickets assigned to the signed-in staff member, optionally of one status. */
    @GetMapping("/assigned")
    String assignedTickets(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        if (!accessPolicy.canViewAssignedTickets(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only staff members have tickets assigned to them");
        }
        model.addAttribute("pageTitle", "Assigned to Me");
        model.addAttribute("listUrl", "/tickets/assigned");
        model.addAttribute("showCreator", true);
        model.addAttribute("canCreate", false);
        model.addAttribute("emptyTitle", "No tickets assigned to you");
        model.addAttribute("emptyDescription", "New tickets in your categories will show up here.");
        return renderList(TicketScope.ASSIGNED_TO, status, pageable, model);
    }

    private String renderList(TicketScope scope, @Nullable String status, Pageable pageable, Model model) {
        var userId = AuthUtils.getCurrentUserIdOrThrow();
        var selected = parseStatus(status);
        var counts = ticketService.countByStatus(scope, userId);
        model.addAttribute("tickets", ticketService.findTickets(scope, userId, selected, pageable));
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("selectedStatus", selected);
        model.addAttribute("statusCounts", counts);
        model.addAttribute(
                "totalCount",
                counts.values().stream().mapToLong(Long::longValue).sum());
        return "tickets/list";
    }

    /** An unknown status shows every ticket rather than an error page. */
    private static @Nullable TicketStatus parseStatus(@Nullable String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TicketStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
