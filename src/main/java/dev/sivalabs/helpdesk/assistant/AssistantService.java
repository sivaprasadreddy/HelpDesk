package dev.sivalabs.helpdesk.assistant;

import dev.sivalabs.helpdesk.knowledgebase.KnowledgeBaseAPI;
import dev.sivalabs.helpdesk.knowledgebase.KnowledgeSnippet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * Answers questions from the community knowledge base: finds the sections closest to the question and asks the chat
 * model to answer from them only, with the recent conversation for follow-up questions.
 */
@Service
class AssistantService {
    private static final Logger LOG = LoggerFactory.getLogger(AssistantService.class);

    static final int CONTEXT_SECTIONS = 4;
    static final int HISTORY_TURNS = 10;

    static final String SYSTEM_PROMPT = """
            You are the HelpDesk assistant of a gated residential community. You help residents, staff and
            administrators with questions about the community's rules, amenities, maintenance and emergencies.

            Answer only from the CONTEXT below, which comes from the community's documents. If the context does not
            contain the answer, say that you do not know and suggest contacting the association office or raising a
            ticket for maintenance problems. Never invent rules, timings, fees or phone numbers.
            Keep answers short and practical. Use plain text without Markdown.

            CONTEXT:
            %s
            """;

    private final ChatClient chatClient;
    private final KnowledgeBaseAPI knowledgeBase;

    AssistantService(ChatClient.Builder chatClientBuilder, KnowledgeBaseAPI knowledgeBase) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeBase = knowledgeBase;
    }

    AssistantReply ask(List<ChatTurn> history, String question) {
        try {
            var snippets = knowledgeBase.search(question, CONTEXT_SECTIONS);
            var answer = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT.formatted(context(snippets)))
                    .messages(historyMessages(history))
                    .user(question)
                    .call()
                    .content();
            var sources = snippets.stream()
                    .map(s -> s.documentTitle() + " - " + s.section())
                    .distinct()
                    .toList();
            LOG.info("Assistant answered a question using {} knowledge base sections", snippets.size());
            return new AssistantReply(answer == null ? "" : answer.strip(), sources);
        } catch (RuntimeException e) {
            LOG.error("Assistant failed to answer", e);
            throw new AssistantUnavailableException(e);
        }
    }

    private static String context(List<KnowledgeSnippet> snippets) {
        if (snippets.isEmpty()) {
            return "(no matching documents)";
        }
        return snippets.stream().map(KnowledgeSnippet::text).collect(Collectors.joining("\n\n---\n\n"));
    }

    /** The last turns before the question, without failed answers. */
    private static List<Message> historyMessages(List<ChatTurn> history) {
        var usable = history.stream().filter(t -> !t.error()).toList();
        var recent = usable.subList(Math.max(0, usable.size() - HISTORY_TURNS), usable.size());
        var messages = new ArrayList<Message>();
        for (var turn : recent) {
            messages.add(turn.isUser() ? new UserMessage(turn.text()) : new AssistantMessage(turn.text()));
        }
        return messages;
    }
}
