package dev.sivalabs.helpdesk.tickets;

import java.time.LocalDate;

/** REQ-024: tickets created and tickets resolved on one day. */
public record DailyTicketFlow(LocalDate date, long created, long resolved) {}
