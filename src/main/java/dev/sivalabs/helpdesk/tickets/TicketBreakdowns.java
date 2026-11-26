package dev.sivalabs.helpdesk.tickets;

import java.util.List;

/** The number of tickets by status, priority and category. */
public record TicketBreakdowns(
        long total, List<CountByGroup> byStatus, List<CountByGroup> byPriority, List<CountByGroup> byCategory) {}
