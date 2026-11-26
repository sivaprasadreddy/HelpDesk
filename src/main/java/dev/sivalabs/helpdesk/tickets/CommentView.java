package dev.sivalabs.helpdesk.tickets;

import java.time.Instant;

/** A comment with its author's name resolved. */
public record CommentView(Long id, String authorName, boolean byRaiser, String content, Instant createdAt) {}
