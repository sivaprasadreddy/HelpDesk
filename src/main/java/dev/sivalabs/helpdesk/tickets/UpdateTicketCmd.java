package dev.sivalabs.helpdesk.tickets;

record UpdateTicketCmd(Long id, String title, String description, TicketPriority priority, Long categoryId) {}
