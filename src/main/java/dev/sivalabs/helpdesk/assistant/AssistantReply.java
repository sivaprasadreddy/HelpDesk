package dev.sivalabs.helpdesk.assistant;

import java.util.List;

/** The assistant's answer and the knowledge base sections it was given. */
record AssistantReply(String text, List<String> sources) {}
