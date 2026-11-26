package dev.sivalabs.helpdesk.assistant;

import java.util.List;

/** The assistant's answer, the knowledge base sections it was given and the tickets it proposed. */
record AssistantReply(String text, List<String> sources, List<TicketDraft> drafts) {}
