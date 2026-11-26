package dev.sivalabs.helpdesk.tickets;

import java.util.List;

/** REQ-023: the number of tickets by status, priority and category. */
public record TicketBreakdowns(
        long total, List<CountByGroup> byStatus, List<CountByGroup> byPriority, List<CountByGroup> byCategory) {}
