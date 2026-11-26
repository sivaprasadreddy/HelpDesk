package dev.sivalabs.helpdesk.assistant;

/** The AI model could not be reached or failed to answer. */
class AssistantUnavailableException extends RuntimeException {
    AssistantUnavailableException(Throwable cause) {
        super("The assistant is not available right now", cause);
    }
}
