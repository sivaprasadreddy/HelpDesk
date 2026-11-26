package dev.sivalabs.helpdesk.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.FakeChatModel;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

class AssistantControllerTests extends BaseIT {
    static final SecurityUser RESIDENT = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser STAFF = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);

    @Autowired
    FakeChatModel chatModel;

    @AfterEach
    void resetModel() {
        chatModel.reset();
    }

    void ask(MockHttpSession session, String question) {
        assertThat(mvc.post()
                        .uri("/assistant")
                        .session(session)
                        .with(user(RESIDENT))
                        .with(csrf())
                        .param("message", question))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/assistant#latest");
    }

    @Test
    void shouldShowAnEmptyConversationToAnySignedInUser() {
        assertThat(mvc.get().uri("/assistant").with(user(STAFF)))
                .hasStatusOk()
                .bodyText()
                .contains("Ask the assistant")
                .contains("How can I help?");
        assertThat(mvc.get().uri("/home").with(user(STAFF))).bodyText().contains("href=\"/assistant\"");
    }

    @Test
    void shouldRedirectAnonymousToLogin() {
        assertThat(mvc.get().uri("/assistant")).hasStatus3xxRedirection();
    }

    @Test
    void shouldAnswerFromTheKnowledgeBaseAndShowSources() {
        chatModel.answerWith(prompt -> FakeChatModel.text("The pool is open from 6 AM to 9 AM and 4 PM to 8 PM."));
        var session = new MockHttpSession();

        ask(session, "When is the swimming pool open?");

        var system = chatModel.lastPrompt().getSystemMessage().getText();
        assertThat(system)
                .contains("Answer questions about the community only from the CONTEXT")
                .contains("6 AM to 9 AM");
        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("When is the swimming pool open?")
                .contains("The pool is open from 6 AM to 9 AM and 4 PM to 8 PM.")
                .contains("Amenities - Swimming pool");
    }

    @Test
    void shouldSendEarlierTurnsForFollowUpQuestions() {
        var session = new MockHttpSession();
        ask(session, "Can I keep a dog?");
        ask(session, "Is it allowed in the gym?");

        var messages = chatModel.lastPrompt().getInstructions();
        assertThat(messages.stream().map(m -> m.getMessageType()))
                .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("Can I keep a dog?");
        assertThat(messages.get(2).getText()).isEqualTo("Test answer to: Can I keep a dog?");
    }

    @Test
    void shouldApologiseWhenTheModelIsUnavailable() {
        chatModel.failWith(new IllegalStateException("Connection refused"));
        var session = new MockHttpSession();

        ask(session, "When is the gym open?");

        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("the AI service is not available");
    }

    @Test
    void shouldRejectABlankQuestion() {
        assertThat(mvc.post()
                        .uri("/assistant")
                        .with(user(RESIDENT))
                        .with(csrf())
                        .param("message", "  "))
                .hasStatusOk()
                .bodyText()
                .contains("Type a question");
        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    void shouldStartANewConversation() {
        var session = new MockHttpSession();
        ask(session, "When is the gym open?");

        mvc.post()
                .uri("/assistant/clear")
                .session(session)
                .with(user(RESIDENT))
                .with(csrf())
                .exchange();

        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("How can I help?")
                .doesNotContain("When is the gym open?");
    }

    @Test
    void shouldShowTheQuestionAndASpinnerBeforeTheModelAnswers() throws Exception {
        var session = new MockHttpSession();

        var sent = mvc.post()
                .uri("/assistant")
                .session(session)
                .header("HX-Request", "true")
                .with(user(RESIDENT))
                .with(csrf())
                .param("message", "When is the gym open?")
                .exchange();

        assertThat(sent).hasStatusOk();
        var body = sent.getResponse().getContentAsString();
        assertThat(body)
                .contains("When is the gym open?")
                .contains("chat-turn-pending")
                .doesNotContain("<html");
        assertThat(chatModel.lastPrompt()).as("the model is not called yet").isNull();
        var matcher = java.util.regex.Pattern.compile("hx-get=\"(/assistant/replies/[0-9a-f-]+)\"")
                .matcher(body);
        assertThat(matcher.find()).isTrue();
        var replyUrl = matcher.group(1);

        // A reload while the answer is pending keeps the spinner.
        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains(replyUrl);

        var reply = mvc.get().uri(replyUrl).session(session).with(user(RESIDENT));
        assertThat(reply)
                .hasStatusOk()
                .bodyText()
                .contains("Test answer to: When is the gym open?")
                .doesNotContain("chat-turn-pending");
        assertThat(mvc.get().uri("/assistant").session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("Test answer to: When is the gym open?")
                .doesNotContain(replyUrl);

        // Asking for the same reply again does not call the model a second time.
        chatModel.reset();
        assertThat(mvc.get().uri(replyUrl).session(session).with(user(RESIDENT)))
                .bodyText()
                .contains("Test answer to: When is the gym open?");
        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    void shouldReturnAValidationNoticeToHtmx() {
        assertThat(mvc.post()
                        .uri("/assistant")
                        .header("HX-Request", "true")
                        .with(user(RESIDENT))
                        .with(csrf())
                        .param("message", " "))
                .hasStatusOk()
                .bodyText()
                .contains("Type a question")
                .doesNotContain("<html");
    }
}
