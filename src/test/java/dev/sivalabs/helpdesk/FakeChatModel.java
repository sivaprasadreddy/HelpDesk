package dev.sivalabs.helpdesk;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A chat model for tests: records the last prompt and answers with whatever the test set ({@link #answerWith}),
 * by default "Test answer to: " and the user's message.
 */
public class FakeChatModel implements ChatModel {
    private static final Function<Prompt, ChatResponse> DEFAULT =
            prompt -> text("Test answer to: " + prompt.getUserMessage().getText());

    private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
    private final AtomicReference<Function<Prompt, ChatResponse>> responder = new AtomicReference<>(DEFAULT);

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt.set(prompt);
        return responder.get().apply(prompt);
    }

    public Prompt lastPrompt() {
        return lastPrompt.get();
    }

    public void answerWith(Function<Prompt, ChatResponse> responder) {
        this.responder.set(responder);
    }

    public void failWith(RuntimeException e) {
        answerWith(prompt -> {
            throw e;
        });
    }

    public void reset() {
        lastPrompt.set(null);
        responder.set(DEFAULT);
    }

    public static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
