package dev.sivalabs.helpdesk.tickets;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * The administrator's ticket list filters; a {@code null} field does not filter. {@code unassigned} selects
 * tickets with no assignee and takes precedence over {@code assigneeId}.
 */
public record TicketFilter(
        @Nullable TicketStatus status,
        @Nullable TicketPriority priority,
        @Nullable Long categoryId,
        @Nullable Long assigneeId,
        boolean unassigned) {

    static final String UNASSIGNED = "none";

    /** Parses request parameters; unknown values are ignored rather than rejected. */
    static TicketFilter parse(
            @Nullable String status, @Nullable String priority, @Nullable String category, @Nullable String assignee) {
        var unassigned = assignee != null && UNASSIGNED.equalsIgnoreCase(assignee.trim());
        return new TicketFilter(
                parseEnum(TicketStatus.class, status),
                parseEnum(TicketPriority.class, priority),
                parseLong(category),
                unassigned ? null : parseLong(assignee),
                unassigned);
    }

    public boolean isActive() {
        return status != null || priority != null || categoryId != null || assigneeId != null || unassigned;
    }

    Specification<TicketEntity> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (categoryId != null) predicates.add(cb.equal(root.get("categoryId"), categoryId));
            if (unassigned) predicates.add(cb.isNull(root.get("assignedTo")));
            else if (assigneeId != null) predicates.add(cb.equal(root.get("assignedTo"), assigneeId));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static <E extends Enum<E>> @Nullable E parseEnum(Class<E> type, @Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable Long parseLong(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
