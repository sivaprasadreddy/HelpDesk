package dev.sivalabs.helpdesk.tickets;

import java.util.List;

/**
 * The ticket workflow: OPEN → IN_PROGRESS → RESOLVED → CLOSED. Work can be paused (IN_PROGRESS → OPEN) and a
 * resolution that did not fix the problem reopened (RESOLVED → IN_PROGRESS). CLOSED is final.
 */
public enum TicketStatus {
    OPEN("Open"),
    IN_PROGRESS("In progress"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** The statuses a ticket in this status can move to, the usual next step first. */
    public List<TicketStatus> nextStatuses() {
        return switch (this) {
            case OPEN -> List.of(IN_PROGRESS);
            case IN_PROGRESS -> List.of(RESOLVED, OPEN);
            case RESOLVED -> List.of(CLOSED, IN_PROGRESS);
            case CLOSED -> List.of();
        };
    }

    public boolean canMoveTo(TicketStatus target) {
        return nextStatuses().contains(target);
    }
}
