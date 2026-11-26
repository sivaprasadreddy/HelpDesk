package dev.sivalabs.helpdesk.home;

/** A home page shortcut: a link to a common action, with a Lucide icon and a one-line description. */
record Shortcut(String title, String description, String href, String icon) {}
