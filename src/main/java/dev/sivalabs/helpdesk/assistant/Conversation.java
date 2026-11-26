package dev.sivalabs.helpdesk.assistant;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The signed-in user's conversation with the assistant, kept in their HTTP session: it ends when they sign out, and
 * only the most recent {@link #MAX_TURNS} turns are kept.
 */
final class Conversation implements Serializable {
    static final int MAX_TURNS = 40;
    private static final String ATTRIBUTE = Conversation.class.getName();

    private final ArrayList<ChatTurn> turns = new ArrayList<>();
    private final HashMap<String, Long> createdTickets = new HashMap<>();

    static Conversation of(HttpSession session) {
        if (session.getAttribute(ATTRIBUTE) instanceof Conversation conversation) {
            return conversation;
        }
        var conversation = new Conversation();
        session.setAttribute(ATTRIBUTE, conversation);
        return conversation;
    }

    static void clear(HttpSession session) {
        session.removeAttribute(ATTRIBUTE);
    }

    synchronized List<ChatTurn> turns() {
        return List.copyOf(turns);
    }

    /** A draft proposed in this conversation, if it is still shown. */
    synchronized Optional<TicketDraft> findDraft(String draftId) {
        return turns.stream()
                .flatMap(t -> t.drafts().stream())
                .filter(d -> d.id().equals(draftId))
                .findFirst();
    }

    /** The tickets created from drafts of this conversation: draft id to ticket id. */
    synchronized Map<String, Long> createdTickets() {
        return Map.copyOf(createdTickets);
    }

    synchronized void markCreated(String draftId, Long ticketId) {
        createdTickets.put(draftId, ticketId);
    }

    synchronized void add(ChatTurn turn) {
        turns.add(turn);
        while (turns.size() > MAX_TURNS) {
            turns.removeFirst();
        }
    }
}
