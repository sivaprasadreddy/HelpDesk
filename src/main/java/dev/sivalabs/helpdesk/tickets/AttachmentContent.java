package dev.sivalabs.helpdesk.tickets;

/** An attachment's bytes and what to serve them as. */
record AttachmentContent(String fileName, String contentType, byte[] data) {}
