package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.AuthUtils;
import dev.sivalabs.helpdesk.users.UserAPI;
import dev.sivalabs.helpdesk.users.UserDto;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Creating tickets (ADMIN and USER, see {@link AccessPolicy#canCreateTicket}), the resident's own ticket list
 * ({@link AccessPolicy#canViewOwnTickets}) and the staff member's assigned list
 * ({@link AccessPolicy#canViewAssignedTickets}).
 */
@Controller
@RequestMapping("/tickets")
class TicketController {
    private static final Logger LOG = LoggerFactory.getLogger(TicketController.class);

    private static final String CREATE_VIEW = "tickets/create";
    private static final String EDIT_VIEW = "tickets/edit";
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
                        AuthUtils.getCurrentUserIdOrThrow(),
                        readImages(form.images()));
                var id = ticketService.createTicket(cmd);
                redirectAttributes.addFlashAttribute("successMessage", "Ticket #" + id + " created successfully");
                return "redirect:" + ticketListUrl(authentication);
            } catch (InvalidTicketCategoryException e) {
                LOG.warn("Ticket creation refused: {}", e.getMessage());
                bindingResult.rejectValue("categoryId", "category.invalid", "Choose an active category");
            } catch (InvalidAttachmentException e) {
                LOG.warn("Ticket creation refused: {}", e.getMessage());
                bindingResult.rejectValue("images", "images.invalid", e.getMessage());
            }
        }
        addFormOptions(authentication, model);
        return CREATE_VIEW;
    }

    /** Every ticket, filtered by status, priority, category and assignee ({@code assignee=none}). */
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

    /** One ticket; 404 when it does not exist or the user may not view it. */
    @GetMapping("/{id:\\d+}")
    String ticketDetails(Authentication authentication, @PathVariable Long id, Model model) {
        var ticket = ticketService.getTicket(id, authentication.getPrincipal());
        model.addAttribute("ticket", ticket);
        model.addAttribute(
                "canChangeStatus",
                accessPolicy.canUpdateTicketStatus(authentication.getPrincipal(), ticket.assignedTo())
                        && !ticket.status().nextStatuses().isEmpty());
        var canAssign =
                accessPolicy.canAssignTicket(authentication.getPrincipal()) && ticket.status() != TicketStatus.CLOSED;
        model.addAttribute("canAssign", canAssign);
        model.addAttribute("canEdit", accessPolicy.canUpdateTicketDetails(authentication.getPrincipal()));
        model.addAttribute("comments", ticketService.findComments(id, authentication.getPrincipal()));
        model.addAttribute("attachments", ticketService.findAttachments(id, authentication.getPrincipal()));
        model.addAttribute(
                "canComment",
                accessPolicy.canComment(authentication.getPrincipal(), ticket.createdBy(), ticket.assignedTo())
                        && ticket.status() != TicketStatus.CLOSED);
        if (!model.containsAttribute("commentForm")) {
            model.addAttribute("commentForm", CommentForm.empty());
        }
        if (canAssign) {
            model.addAttribute(
                    "activeStaff",
                    userAPI.findStaffMembers().stream().filter(UserDto::active).toList());
        }
        model.addAttribute("listUrl", ticketListUrl(authentication));
        model.addAttribute("listTitle", ticketListTitle(authentication));
        return "tickets/details";
    }

    /** Moves the ticket to the next status; {@code from} is the status the user saw. */
    @PostMapping("/{id:\\d+}/status")
    String changeStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam TicketStatus from,
            @RequestParam TicketStatus to,
            RedirectAttributes redirectAttributes) {
        try {
            ticketService.changeStatus(id, from, to, authentication.getPrincipal());
            redirectAttributes.addFlashAttribute("successMessage", "Ticket #" + id + " moved to " + to.getLabel());
        } catch (InvalidStatusTransitionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    /** Assigns or reassigns the ticket to an active staff member (ADMIN only). */
    @PostMapping("/{id:\\d+}/assignee")
    String assignTicket(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam Long staffId,
            RedirectAttributes redirectAttributes) {
        if (!accessPolicy.canAssignTicket(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only administrators can assign tickets");
        }
        try {
            var changed = ticketService.assignTicket(id, staffId);
            var name = userAPI.findNamesByIds(Set.of(staffId)).getOrDefault(staffId, "the staff member");
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    changed
                            ? "Ticket #" + id + " assigned to " + name
                            : "Ticket #" + id + " is already assigned to " + name);
        } catch (InvalidAssigneeException | InvalidStatusTransitionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    /** The form to edit a ticket's details (ADMIN only). */
    @GetMapping("/{id:\\d+}/edit")
    String editTicketForm(Authentication authentication, @PathVariable Long id, Model model) {
        requireCanUpdateDetails(authentication);
        var ticket = ticketService.getTicket(id, authentication.getPrincipal());
        model.addAttribute("form", UpdateTicketForm.of(ticket, ticket.categoryId()));
        addEditFormOptions(ticket, model);
        return EDIT_VIEW;
    }

    @PostMapping("/{id:\\d+}/edit")
    String updateTicket(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") UpdateTicketForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        requireCanUpdateDetails(authentication);
        if (!bindingResult.hasErrors()) {
            try {
                ticketService.updateTicket(
                        new UpdateTicketCmd(id, form.title(), form.description(), form.priority(), form.categoryId()));
                redirectAttributes.addFlashAttribute("successMessage", "Ticket #" + id + " updated successfully");
                return "redirect:/tickets/" + id;
            } catch (InvalidTicketCategoryException e) {
                bindingResult.rejectValue("categoryId", "category.invalid", "Choose an active category");
            }
        }
        addEditFormOptions(ticketService.getTicket(id, authentication.getPrincipal()), model);
        return EDIT_VIEW;
    }

    /**
     * An attachment's image, for those who may view the ticket. Served with the type detected when it was stored, as
     * an inline download that browsers keep private.
     */
    @GetMapping("/{id:\\d+}/attachments/{attachmentId:\\d+}")
    ResponseEntity<byte[]> attachment(
            Authentication authentication, @PathVariable Long id, @PathVariable Long attachmentId) {
        var content = ticketService.getAttachment(id, attachmentId, authentication.getPrincipal());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(content.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(content.data());
    }

    private static List<ImageUpload> readImages(List<MultipartFile> files) {
        var images = new ArrayList<ImageUpload>();
        for (var file : files) {
            try {
                images.add(new ImageUpload(file.getOriginalFilename(), file.getBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read uploaded image", e);
            }
        }
        return images;
    }

    /** Adds a comment; on a validation error the details page shows it with the text kept. */
    @PostMapping("/{id:\\d+}/comments")
    String addComment(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("commentForm") CommentForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("commentForm", form);
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.commentForm", bindingResult);
            return "redirect:/tickets/" + id + "#comments";
        }
        try {
            ticketService.addComment(
                    id, AuthUtils.getCurrentUserIdOrThrow(), form.content(), authentication.getPrincipal());
            redirectAttributes.addFlashAttribute("successMessage", "Comment added");
        } catch (InvalidStatusTransitionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tickets/" + id + "#comments";
    }

    /** The resident's own tickets, optionally of one status ({@code ?status=OPEN}). */
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

    /** The tickets assigned to the signed-in staff member, optionally of one status. */
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

    private void requireCanUpdateDetails(Authentication authentication) {
        if (!accessPolicy.canUpdateTicketDetails(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only administrators can edit tickets");
        }
    }

    /** Active categories, plus the ticket's own category if it has been deactivated since. */
    private void addEditFormOptions(TicketDetails ticket, Model model) {
        var categories = new ArrayList<>(categoryAPI.findActiveCategories());
        if (categories.stream().noneMatch(c -> c.id().equals(ticket.categoryId()))) {
            categoryAPI.findAllCategories().stream()
                    .filter(c -> c.id().equals(ticket.categoryId()))
                    .findFirst()
                    .ifPresent(c -> categories.addFirst(c));
        }
        model.addAttribute("ticket", ticket);
        model.addAttribute("categories", categories);
        model.addAttribute("priorities", TicketPriority.values());
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
