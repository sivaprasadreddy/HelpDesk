package dev.sivalabs.helpdesk.home;

import dev.sivalabs.helpdesk.users.AccessPolicy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {
    private final AccessPolicy accessPolicy;

    HomeController(AccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/")
    String index(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/home";
        }
        return "index";
    }

    @GetMapping("/login")
    String loginForm(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/home";
        }
        return "login";
    }

    @GetMapping("/home")
    String home(Authentication authentication, Model model) {
        model.addAttribute("shortcuts", shortcutsFor(authentication.getPrincipal()));
        return "home";
    }

    /**
     * The most common actions of the user's role, most frequent first: residents report and follow their issues,
     * staff work through their queue, administrators triage and oversee. Each shortcut is shown only when the
     * {@link AccessPolicy} allows what it links to.
     */
    List<Shortcut> shortcutsFor(Object principal) {
        var shortcuts = new ArrayList<Shortcut>();
        if (accessPolicy.canViewOwnTickets(principal)) {
            shortcuts.add(new Shortcut(
                    "Report an issue",
                    "Raise a new ticket for something that needs fixing.",
                    "/tickets/new",
                    "circle-plus"));
            shortcuts.add(new Shortcut(
                    "My tickets", "Follow the progress of the tickets you raised.", "/tickets/mine", "ticket"));
            shortcuts.add(new Shortcut(
                    "Being worked on",
                    "Your tickets that staff are working on now.",
                    "/tickets/mine?status=IN_PROGRESS",
                    "loader"));
            shortcuts.add(new Shortcut(
                    "Resolved",
                    "Check that the fix worked on your resolved tickets.",
                    "/tickets/mine?status=RESOLVED",
                    "circle-check"));
        }
        if (accessPolicy.canViewAssignedTickets(principal)) {
            shortcuts.add(new Shortcut(
                    "Ready to start",
                    "Tickets assigned to you that are still open.",
                    "/tickets/assigned?status=OPEN",
                    "inbox"));
            shortcuts.add(new Shortcut(
                    "In progress",
                    "The tickets you are working on.",
                    "/tickets/assigned?status=IN_PROGRESS",
                    "wrench"));
            shortcuts.add(new Shortcut(
                    "All my assignments", "Every ticket assigned to you.", "/tickets/assigned", "clipboard-list"));
        }
        if (accessPolicy.canViewAllTickets(principal)) {
            shortcuts.add(new Shortcut(
                    "Unassigned tickets",
                    "Tickets nobody handles yet: assign them to staff.",
                    "/tickets?assignee=none",
                    "user-x"));
            shortcuts.add(new Shortcut(
                    "Critical and open",
                    "Open tickets with critical priority.",
                    "/tickets?status=OPEN&priority=CRITICAL",
                    "triangle-alert"));
            shortcuts.add(new Shortcut("All tickets", "Browse and filter every ticket.", "/tickets", "ticket"));
            shortcuts.add(new Shortcut(
                    "New ticket", "Raise a ticket on a resident's behalf.", "/tickets/new", "circle-plus"));
        }
        if (accessPolicy.canViewAnalytics(principal)) {
            shortcuts.add(new Shortcut(
                    "Analytics",
                    "Ticket trends, resolution times and staff workload.",
                    "/admin/analytics",
                    "chart-column"));
        }
        if (accessPolicy.canManageUsers(principal)) {
            shortcuts.add(new Shortcut(
                    "Add staff member", "Create an account for maintenance staff.", "/admin/staff/new", "hard-hat"));
            shortcuts.add(new Shortcut(
                    "Add resident", "Create an account for a resident.", "/admin/residents/new", "user-plus"));
        }
        return shortcuts;
    }
}
