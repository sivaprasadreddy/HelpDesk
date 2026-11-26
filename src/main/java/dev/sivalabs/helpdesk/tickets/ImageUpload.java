package dev.sivalabs.helpdesk.tickets;

/** An image the user picked, read from the request. */
record ImageUpload(String fileName, byte[] data) {}
