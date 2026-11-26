package dev.sivalabs.helpdesk.users;

/**
 * Published, within the deactivating transaction, when an active staff member is deactivated, so their open
 * work can be handed to someone else.
 */
public record StaffDeactivatedEvent(Long staffId) {}
