package dev.sivalabs.helpdesk.tickets;

import java.time.LocalDate;

/** Tickets created and tickets resolved on one day. */
public record DailyTicketFlow(LocalDate date, long created, long resolved) {}
