package dev.sivalabs.helpdesk.assistant;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The signed-in user's conversation with the assistant, kept in their HTTP session: it ends when they sign out, and
 * only the most recent {@link #MAX_TURNS} turns are kept.
 */
final class Conversation implements Serializable {
    static final int MAX_TURNS = 40;
    private static final String ATTRIBUTE = Conversation.class.getName();

    private final ArrayList<ChatTurn> turns = new ArrayList<>();

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

    synchronized void add(ChatTurn turn) {
        turns.add(turn);
        while (turns.size() > MAX_TURNS) {
            turns.removeFirst();
        }
    }
}
