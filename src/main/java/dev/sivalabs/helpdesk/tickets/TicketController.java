package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.AuthUtils;
import dev.sivalabs.helpdesk.users.UserAPI;
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
import org.springframework.web.bind.annotation.PathVariable;
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
    private final UserAPI userAPI;

    TicketController(TicketService ticketService, CategoryAPI categoryAPI, AccessPolicy accessPolicy, UserAPI userAPI) {
        this.ticketService = ticketService;
        this.categoryAPI = categoryAPI;
        this.accessPolicy = accessPolicy;
        this.userAPI = userAPI;
    }

    @GetMapping("/new")
    String newTicketForm(Authentication authentication, Model model) {
        requireCanCreate(authentication);
        model.addAttribute("ticket", CreateTicketForm.empty());
        addFormOptions(authentication, model);
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
                return "redirect:" + ticketListUrl(authentication);
            } catch (InvalidTicketCategoryException e) {
                bindingResult.rejectValue("categoryId", "category.invalid", "Choose an active category");
            }
        }
        addFormOptions(authentication, model);
        return CREATE_VIEW;
    }

    /** REQ-016: every ticket, filtered by status, priority, category and assignee ({@code assignee=none}). */
    @GetMapping
    String allTickets(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String assignee,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {
        if (!accessPolicy.canViewAllTickets(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only administrators can view all tickets");
        }
        var filter = TicketFilter.parse(status, priority, category, assignee);
        model.addAttribute("pageTitle", "All Tickets");
        model.addAttribute("listUrl", "/tickets");
        model.addAttribute("adminView", true);
        model.addAttribute("showCreator", true);
        model.addAttribute("canCreate", true);
        model.addAttribute("emptyTitle", "No tickets yet");
        model.addAttribute("emptyDescription", "Tickets raised by residents will show up here.");
        model.addAttribute("tickets", ticketService.findAllTickets(filter, pageable));
        model.addAttribute("totalCount", ticketService.hasAnyTickets() ? 1 : 0);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", TicketPriority.values());
        model.addAttribute("categories", categoryAPI.findAllCategories());
        model.addAttribute("staff", userAPI.findStaffMembers());
        return "tickets/list";
    }

    /** REQ-017: one ticket; 404 when it does not exist or the user may not view it. */
    @GetMapping("/{id:\\d+}")
    String ticketDetails(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("ticket", ticketService.getTicket(id, authentication.getPrincipal()));
        model.addAttribute("listUrl", ticketListUrl(authentication));
        model.addAttribute("listTitle", ticketListTitle(authentication));
        return "tickets/details";
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
        model.addAttribute("adminView", false);
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

    /** The ticket list of the user's role: own tickets (USER), assigned tickets (STAFF), every ticket (ADMIN). */
    private String ticketListUrl(Authentication authentication) {
        var principal = authentication.getPrincipal();
        if (accessPolicy.canViewOwnTickets(principal)) return "/tickets/mine";
        if (accessPolicy.canViewAssignedTickets(principal)) return "/tickets/assigned";
        return "/tickets";
    }

    private String ticketListTitle(Authentication authentication) {
        var principal = authentication.getPrincipal();
        if (accessPolicy.canViewOwnTickets(principal)) return "My Tickets";
        if (accessPolicy.canViewAssignedTickets(principal)) return "Assigned to Me";
        return "All Tickets";
    }

    private void requireCanCreate(Authentication authentication) {
        if (!accessPolicy.canCreateTicket(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only administrators and residents can create tickets");
        }
    }

    private void addFormOptions(Authentication authentication, Model model) {
        model.addAttribute("listUrl", ticketListUrl(authentication));
        model.addAttribute("priorities", TicketPriority.values());
        model.addAttribute("categories", categoryAPI.findActiveCategories());
    }
}
