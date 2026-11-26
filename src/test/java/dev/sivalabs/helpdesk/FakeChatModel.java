package dev.sivalabs.helpdesk;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;

/**
 * A chat model for tests: records the last prompt and answers with whatever the test set ({@link #answerWith}),
 * by default "Test answer to: " and the user's message.
 */
public class FakeChatModel implements ChatModel {
    private static final Function<Prompt, ChatResponse> DEFAULT =
            prompt -> text("Test answer to: " + prompt.getUserMessage().getText());

    private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
    private final AtomicReference<Function<Prompt, ChatResponse>> responder = new AtomicReference<>(DEFAULT);

    private final ToolCallingManager toolCallingManager =
            ToolCallingManager.builder().build();

    /** Runs the tools the responder asks for and calls it again with their results, as real models do. */
    @Override
    public ChatResponse call(Prompt prompt) {
        var current = prompt;
        for (int round = 0; round < 5; round++) {
            lastPrompt.set(current);
            var response = responder.get().apply(current);
            if (!response.hasToolCalls()) {
                return response;
            }
            var result = toolCallingManager.executeToolCalls(current, response);
            current = new Prompt(result.conversationHistory(), current.getOptions());
        }
        throw new IllegalStateException("Too many tool calling rounds");
    }

    /** Tool calling options, so the chat client passes the tools on as it does to real models. */
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    /** The names of the tools offered in the last prompt. */
    public List<String> lastToolNames() {
        if (lastPrompt.get().getOptions() instanceof ToolCallingChatOptions options
                && options.getToolCallbacks() != null) {
            return options.getToolCallbacks().stream()
                    .map(c -> c.getToolDefinition().name())
                    .toList();
        }
        return List.of();
    }

    /** A response that asks to call one tool with JSON arguments. */
    public static ChatResponse toolCall(String name, String arguments) {
        var message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name, arguments)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    /** The text of the last tool result in the prompt, if the last message is one. */
    public static String lastToolResult(Prompt prompt) {
        var messages = prompt.getInstructions();
        if (messages.getLast() instanceof ToolResponseMessage response) {
            return response.getResponses().getLast().responseData();
        }
        return null;
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
