package dev.sivalabs.helpdesk.users;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The permissions matrix from docs/project.md, in one place.
 *
 * <pre>
 * Capability                 ADMIN  STAFF     USER
 * Create tickets             yes    no        yes
 * View tickets               all    assigned  own
 * Assign / reassign tickets  yes    no        no
 * Update ticket status       yes    assigned  no
 * Add comments               yes    assigned  own
 * Manage staff/residents     yes    no        no
 * Manage ticket categories   yes    no        no
 * View analytics             yes    no        no
 * </pre>
 *
 * Checks compare the role exactly instead of using the role hierarchy, so the
 * "assigned" and "own" rules cannot leak between STAFF and USER. Available in
 * SpEL as {@code @accessPolicy}, e.g. {@code @PreAuthorize("@accessPolicy.canCreateTicket(principal)")}.
 */
@Component("accessPolicy")
public class AccessPolicy {

    public boolean canCreateTicket(@Nullable Object principal) {
        return is(principal, Role.ROLE_ADMIN) || is(principal, Role.ROLE_USER);
    }

    public boolean canViewTicket(@Nullable Object principal, Long createdBy, @Nullable Long assignedTo) {
        return is(principal, Role.ROLE_ADMIN)
                || (is(principal, Role.ROLE_STAFF) && isSelf(principal, assignedTo))
                || (is(principal, Role.ROLE_USER) && isSelf(principal, createdBy));
    }

    public boolean canAssignTicket(@Nullable Object principal) {
        return isAdmin(principal);
    }

    public boolean canUpdateTicketStatus(@Nullable Object principal, @Nullable Long assignedTo) {
        return is(principal, Role.ROLE_ADMIN) || (is(principal, Role.ROLE_STAFF) && isSelf(principal, assignedTo));
    }

    public boolean canUpdateTicketDetails(@Nullable Object principal) {
        return isAdmin(principal);
    }

    public boolean canComment(@Nullable Object principal, Long createdBy, @Nullable Long assignedTo) {
        return canViewTicket(principal, createdBy, assignedTo);
    }

    public boolean canManageUsers(@Nullable Object principal) {
        return isAdmin(principal);
    }

    public boolean canManageCategories(@Nullable Object principal) {
        return isAdmin(principal);
    }

    public boolean canViewAnalytics(@Nullable Object principal) {
        return isAdmin(principal);
    }

    private static boolean isAdmin(@Nullable Object principal) {
        return is(principal, Role.ROLE_ADMIN);
    }

    private static boolean is(@Nullable Object principal, Role role) {
        return principal instanceof SecurityUser user && user.getRole() == role;
    }

    private static boolean isSelf(@Nullable Object principal, @Nullable Long userId) {
        return userId != null && principal instanceof SecurityUser user && Objects.equals(user.getId(), userId);
    }
}
