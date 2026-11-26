package dev.sivalabs.helpdesk.tickets;

record CreateTicketCmd(String title, String description, TicketPriority priority, Long categoryId, Long createdBy) {}
