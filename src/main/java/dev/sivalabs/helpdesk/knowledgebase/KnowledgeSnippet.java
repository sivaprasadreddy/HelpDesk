package dev.sivalabs.helpdesk.knowledgebase;

/** A section of a knowledge base document that matched a question. */
public record KnowledgeSnippet(String source, String documentTitle, String section, String text, double score) {}
