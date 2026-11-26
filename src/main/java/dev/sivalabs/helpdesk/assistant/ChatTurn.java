package dev.sivalabs.helpdesk.assistant;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * One message of a conversation with the assistant.
 *
 * @param sources the knowledge base sections an answer drew on, as "Document - Section"; empty for the user's turns
 * @param error whether this is an apology because the assistant could not answer
 */
public record ChatTurn(Role role, String text, List<String> sources, boolean error, Instant at)
        implements Serializable {

    public enum Role {
        USER,
        ASSISTANT
    }

    public ChatTurn {
        sources = List.copyOf(sources);
    }

    static ChatTurn user(String text) {
        return new ChatTurn(Role.USER, text, List.of(), false, Instant.now());
    }

    static ChatTurn assistant(String text, List<String> sources) {
        return new ChatTurn(Role.ASSISTANT, text, sources, false, Instant.now());
    }

    static ChatTurn failure(String text) {
        return new ChatTurn(Role.ASSISTANT, text, List.of(), true, Instant.now());
    }

    public boolean isUser() {
        return role == Role.USER;
    }
}
