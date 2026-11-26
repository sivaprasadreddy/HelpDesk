package dev.sivalabs.helpdesk.assistant;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.knowledgebase.KnowledgeBaseAPI;
import dev.sivalabs.helpdesk.knowledgebase.KnowledgeSnippet;
import dev.sivalabs.helpdesk.tickets.TicketAPI;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Answers questions from the community knowledge base: finds the sections closest to the question and asks the chat
 * model to answer from them only, with the recent conversation for follow-up questions. Can raise tickets too.
 */
@Service
class AssistantService {
    private static final Logger LOG = LoggerFactory.getLogger(AssistantService.class);

    static final int CONTEXT_SECTIONS = 4;
    static final int HISTORY_TURNS = 10;

    static final String SYSTEM_PROMPT = """
            You are the HelpDesk assistant of a gated residential community. You help residents, staff and
            administrators with questions about the community's rules, amenities, maintenance and emergencies.

            Answer questions about the community only from the CONTEXT below, which comes from the community's
            documents. If the context does not contain the answer, say that you do not know and suggest contacting the
            association office. Never invent rules, timings, fees or phone numbers.
            Keep answers short and practical. Use plain text without Markdown.

            %s

            CONTEXT:
            %s
            """;

    static final String CAN_CREATE_TICKETS = """
            You can also prepare maintenance tickets for the signed-in user.
            When the user reports a problem that needs fixing:
            - Work out a short title and a description that says what is wrong and where (block, flat, floor). Ask
              for anything important that is missing.
            - Pick the best matching category from listCategories, and a priority: LOW, MEDIUM, HIGH, or CRITICAL
              only for emergencies such as flooding, fire risk, a power failure or someone stuck in a lift.
            - Call proposeTicket once per problem. The user sees the draft and creates the ticket with the Create
              ticket button. Never say that a ticket has been created.""";

    static final String CANNOT_CREATE_TICKETS = """
            You cannot raise tickets for this user. If they report a problem, tell them that residents and
            administrators raise tickets.""";

    private final ChatClient chatClient;
    private final KnowledgeBaseAPI knowledgeBase;
    private final TicketAPI ticketAPI;
    private final CategoryAPI categoryAPI;
    private final AccessPolicy accessPolicy;

    AssistantService(
            ChatClient.Builder chatClientBuilder,
            KnowledgeBaseAPI knowledgeBase,
            TicketAPI ticketAPI,
            CategoryAPI categoryAPI,
            AccessPolicy accessPolicy) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeBase = knowledgeBase;
        this.ticketAPI = ticketAPI;
        this.categoryAPI = categoryAPI;
        this.accessPolicy = accessPolicy;
    }

    boolean canCreateTickets(Object principal) {
        return accessPolicy.canCreateTicket(principal) && principal instanceof SecurityUser;
    }

    /**
     * Answers the question; for users who may create tickets, the model is also given {@link TicketTools} to propose
     * tickets.
     */
    AssistantReply ask(List<ChatTurn> history, String question, Object principal) {
        try {
            var snippets = knowledgeBase.search(question, CONTEXT_SECTIONS);
            var tools = canCreateTickets(principal) ? new TicketTools(categoryAPI) : null;
            var prompt = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT.formatted(
                            tools == null ? CANNOT_CREATE_TICKETS : CAN_CREATE_TICKETS, context(snippets)))
                    .messages(historyMessages(history))
                    .user(question);
            if (tools != null) {
                prompt = prompt.tools(tools);
            }
            var answer = prompt.call().content();
            var sources = snippets.stream()
                    .map(s -> s.documentTitle() + " - " + s.section())
                    .distinct()
                    .toList();
            LOG.info("Assistant answered a question using {} knowledge base sections", snippets.size());
            var drafts = tools == null ? List.<TicketDraft>of() : tools.drafts();
            return new AssistantReply(answer == null ? "" : answer.strip(), sources, drafts);
        } catch (RuntimeException e) {
            LOG.error("Assistant failed to answer", e);
            throw new AssistantUnavailableException(e);
        }
    }

    /**
     * Creates the ticket the user confirmed, raised by them and assigned as from the New Ticket page.
     *
     * @throws IllegalArgumentException when the draft's category is no longer active
     */
    Long createTicket(TicketDraft draft, Object principal) {
        if (!canCreateTickets(principal)) {
            throw new AccessDeniedException("You cannot create tickets");
        }
        var userId = ((SecurityUser) principal).getId();
        var id = ticketAPI.createTicket(
                draft.title(), draft.description(), draft.priority(), draft.categoryId(), userId);
        LOG.info("Ticket #{} created from an assistant draft by user {}", id, userId);
        return id;
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
