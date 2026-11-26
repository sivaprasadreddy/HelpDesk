package dev.sivalabs.helpdesk.assistant;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * One message of a conversation with the assistant.
 *
 * @param sources the knowledge base sections an answer drew on, as "Document - Section"; empty for the user's turns
 * @param error whether this is an apology because the assistant could not answer
 * @param drafts the tickets the assistant proposed in this turn, for the user to confirm
 */
public record ChatTurn(
        Role role, String text, List<String> sources, boolean error, List<TicketDraft> drafts, Instant at)
        implements Serializable {

    public enum Role {
        USER,
        ASSISTANT
    }

    public ChatTurn {
        sources = List.copyOf(sources);
        drafts = List.copyOf(drafts);
    }

    static ChatTurn user(String text) {
        return new ChatTurn(Role.USER, text, List.of(), false, List.of(), Instant.now());
    }

    static ChatTurn assistant(AssistantReply reply) {
        return new ChatTurn(Role.ASSISTANT, reply.text(), reply.sources(), false, reply.drafts(), Instant.now());
    }

    static ChatTurn info(String text) {
        return new ChatTurn(Role.ASSISTANT, text, List.of(), false, List.of(), Instant.now());
    }

    static ChatTurn failure(String text) {
        return new ChatTurn(Role.ASSISTANT, text, List.of(), true, List.of(), Instant.now());
    }

    public boolean isUser() {
        return role == Role.USER;
    }
}
