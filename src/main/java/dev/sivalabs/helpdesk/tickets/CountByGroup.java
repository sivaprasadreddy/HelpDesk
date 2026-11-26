package dev.sivalabs.helpdesk.tickets;

/** One bar of a breakdown: a group's key (an enum name or a category id), display label and ticket count. */
public record CountByGroup(String key, String label, long count) {}
