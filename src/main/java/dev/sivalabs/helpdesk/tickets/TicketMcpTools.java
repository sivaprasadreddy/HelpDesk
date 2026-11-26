package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.categories.CategoryDto;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ticket operations as MCP tools, served at {@code /mcp} to clients that sign in with HTTP Basic. Each tool runs as
 * the signed-in user and goes through the same {@link AccessPolicy} rules and {@link TicketService} methods as the
 * web pages: a ticket the user may not view is reported as not found.
 */
@Component
class TicketMcpTools {
    private static final Logger LOG = LoggerFactory.getLogger(TicketMcpTools.class);
    static final int PAGE_SIZE = 20;

    private final TicketService ticketService;
    private final CategoryAPI categoryAPI;
    private final AccessPolicy accessPolicy;

    TicketMcpTools(TicketService ticketService, CategoryAPI categoryAPI, AccessPolicy accessPolicy) {
        this.ticketService = ticketService;
        this.categoryAPI = categoryAPI;
        this.accessPolicy = accessPolicy;
    }

    /** A ticket with its comments, as the details page shows it. */
    record TicketWithComments(TicketDetails ticket, List<CommentView> comments) {}

    @McpTool(name = "list_categories", description = "List the active ticket categories with their ids.")
    public List<CategoryDto> listCategories() {
        currentUser();
        return categoryAPI.findActiveCategories();
    }

    @McpTool(name = "list_tickets", description = """
                    List the signed-in user's tickets, newest first, 20 per page: the tickets a resident raised, the
                    tickets assigned to a staff member, or every ticket for an administrator.""")
    public List<TicketSummary> listTickets(
            @McpToolParam(
                            description = "Only tickets in this status: OPEN, IN_PROGRESS, RESOLVED or CLOSED",
                            required = false)
                    @Nullable
                    String status,
            @McpToolParam(description = "Page number, starting at 0", required = false) @Nullable Integer page) {
        var user = currentUser();
        var parsedStatus = status == null || status.isBlank() ? null : parseStatus(status);
        var pageable = PageRequest.of(page == null || page < 0 ? 0 : page, PAGE_SIZE, TicketService.DEFAULT_SORT);
        if (accessPolicy.canViewAllTickets(user)) {
            var filter = TicketFilter.parse(status, null, null, null);
            return ticketService.findAllTickets(filter, pageable).getContent();
        }
        var scope = accessPolicy.canViewAssignedTickets(user) ? TicketScope.ASSIGNED_TO : TicketScope.RAISED_BY;
        return ticketService
                .findTickets(scope, user.getId(), parsedStatus, pageable)
                .getContent();
    }

    @McpTool(name = "get_ticket", description = "Get a ticket's details and comments.")
    public TicketWithComments getTicket(@McpToolParam(description = "Ticket number") Long ticketId) {
        var user = currentUser();
        return new TicketWithComments(
                ticketService.getTicket(ticketId, user), ticketService.findComments(ticketId, user));
    }

    @McpTool(name = "create_ticket", description = """
                    Raise a maintenance ticket as the signed-in user (residents and administrators only). It is assigned
                    automatically to a staff member who handles the category. Returns the new ticket.""")
    public TicketDetails createTicket(
            @McpToolParam(description = "Short summary of the problem, at most 200 characters") String title,
            @McpToolParam(description = "What is wrong and where: block, flat and floor; at most 5000 characters")
                    String description,
            @McpToolParam(description = "LOW, MEDIUM, HIGH or CRITICAL") String priority,
            @McpToolParam(description = "Id of an active category, from list_categories") Long categoryId) {
        var user = currentUser();
        if (!accessPolicy.canCreateTicket(user)) {
            throw new AccessDeniedException("Only residents and administrators can create tickets");
        }
        requireText(title, "title", 200);
        requireText(description, "description", 5000);
        var parsedPriority = parse(TicketPriority.class, priority, "priority");
        try {
            var id = ticketService.createTicket(
                    new CreateTicketCmd(title, description, parsedPriority, categoryId, user.getId()));
            LOG.info("Ticket #{} created through MCP by user {}", id, user.getId());
            return ticketService.getTicket(id, user);
        } catch (InvalidTicketCategoryException e) {
            throw new IllegalArgumentException("Category " + categoryId + " is not an active category", e);
        }
    }

    @McpTool(
            name = "add_comment",
            description =
                    "Add a comment to a ticket the signed-in user may comment on. Closed tickets take no comments.")
    public List<CommentView> addComment(
            @McpToolParam(description = "Ticket number") Long ticketId,
            @McpToolParam(description = "The comment, at most 2000 characters") String content) {
        var user = currentUser();
        requireText(content, "content", 2000);
        ticketService.addComment(ticketId, user.getId(), content, user);
        return ticketService.findComments(ticketId, user);
    }

    @McpTool(name = "change_ticket_status", description = """
                    Move a ticket along its workflow (OPEN, IN_PROGRESS, RESOLVED, CLOSED). Staff may move the tickets
                    assigned to them, administrators any ticket.""")
    public TicketDetails changeStatus(
            @McpToolParam(description = "Ticket number") Long ticketId,
            @McpToolParam(description = "The new status") String status) {
        var user = currentUser();
        var target = parseStatus(status);
        var current = ticketService.getTicket(ticketId, user);
        ticketService.changeStatus(ticketId, current.status(), target, user);
        return ticketService.getTicket(ticketId, user);
    }

    @McpTool(
            name = "assign_ticket",
            description = "Assign or reassign a ticket to an active staff member (administrators only).")
    public TicketDetails assignTicket(
            @McpToolParam(description = "Ticket number") Long ticketId,
            @McpToolParam(description = "User id of the staff member") Long staffId) {
        var user = currentUser();
        if (!accessPolicy.canAssignTicket(user)) {
            throw new AccessDeniedException("Only administrators can assign tickets");
        }
        ticketService.getTicket(ticketId, user);
        ticketService.assignTicket(ticketId, staffId);
        return ticketService.getTicket(ticketId, user);
    }

    private static SecurityUser currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return user;
        }
        throw new AccessDeniedException("Sign in to use the HelpDesk tools");
    }

    private static TicketStatus parseStatus(String status) {
        return parse(TicketStatus.class, status, "status");
    }

    private static <E extends Enum<E>> E parse(Class<E> type, @Nullable String value, String name) {
        try {
            return Enum.valueOf(type, String.valueOf(value).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + name + " '" + value + "'", e);
        }
    }

    private static void requireText(@Nullable String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + name + " is required");
        }
        if (value.strip().length() > max) {
            throw new IllegalArgumentException("The " + name + " must be at most " + max + " characters");
        }
    }
}
