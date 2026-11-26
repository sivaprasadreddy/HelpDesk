package dev.sivalabs.helpdesk.assistant;

import java.io.Serializable;
import java.util.List;

/**
 * A question the user sent that the assistant has not answered yet: the page shows a spinner for it, which asks for
 * the answer in a request of its own.
 *
 * @param history the conversation before the question
 */
public record PendingReply(String id, String question, List<ChatTurn> history) implements Serializable {

    public PendingReply {
        history = List.copyOf(history);
    }
}
