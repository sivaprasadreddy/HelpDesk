package dev.sivalabs.helpdesk.assistant;

import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/** The assistant's chat page, for every signed-in user; ADMIN and USER can also raise tickets through it. */
@Controller
@RequestMapping("/assistant")
class AssistantController {
    private static final String VIEW = "assistant/chat";
    private static final String FRAGMENTS = "assistant/fragments";
    private final AssistantService assistantService;

    AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping
    String chat(Authentication authentication, HttpSession session, Model model) {
        populate(model, Conversation.of(session), authentication);
        model.addAttribute("form", AskForm.empty());
        return VIEW;
    }

    /**
     * Takes the question without waiting for the model. With htmx, answers with the question and a spinner to add to
     * the conversation; the spinner then asks for the answer ({@link #reply}). Without JavaScript, answers the
     * question here and shows the whole page again.
     */
    @PostMapping
    String ask(
            Authentication authentication,
            HttpSession session,
            @RequestHeader(value = "HX-Request", required = false) @Nullable String htmx,
            @Valid @ModelAttribute("form") AskForm form,
            BindingResult bindingResult,
            Model model) {
        var conversation = Conversation.of(session);
        if (bindingResult.hasErrors()) {
            if (htmx != null) {
                model.addAttribute(
                        "message", bindingResult.getFieldErrors().getFirst().getDefaultMessage());
                return FRAGMENTS + " :: notice";
            }
            populate(model, conversation, authentication);
            return VIEW;
        }
        var pending = conversation.ask(form.message().strip());
        if (htmx != null) {
            model.addAttribute("turn", conversation.turns().getLast());
            model.addAttribute("pending", pending);
            return FRAGMENTS + " :: sent";
        }
        answer(conversation, pending, authentication);
        return "redirect:/assistant#latest";
    }

    /** Answers a pending question and returns the answer to put in place of its spinner. */
    @GetMapping("/replies/{id}")
    String reply(Authentication authentication, HttpSession session, @PathVariable String id, Model model) {
        var conversation = Conversation.of(session);
        var pending = conversation.takePending(id);
        if (pending.isEmpty()) {
            // Already answered, for example by a second tab: the latest turn is the answer.
            model.addAttribute("turn", conversation.turns().getLast());
        } else {
            model.addAttribute("turn", answer(conversation, pending.get(), authentication));
        }
        model.addAttribute("createdTickets", conversation.createdTickets());
        return FRAGMENTS + " :: answer";
    }

    private ChatTurn answer(Conversation conversation, PendingReply pending, Authentication authentication) {
        ChatTurn turn;
        try {
            var reply = assistantService.ask(pending.history(), pending.question(), authentication.getPrincipal());
            turn = ChatTurn.assistant(reply);
        } catch (AssistantUnavailableException e) {
            turn = ChatTurn.failure(
                    "Sorry, I can't answer right now because the AI service is not available. Please try again later.");
        }
        conversation.add(turn);
        return turn;
    }

    /** Creates the ticket of a draft the assistant proposed in this conversation, once. */
    @PostMapping("/drafts/{draftId}/create")
    String createFromDraft(Authentication authentication, HttpSession session, @PathVariable String draftId) {
        var conversation = Conversation.of(session);
        var draft = conversation.findDraft(draftId).orElseThrow(() -> new ResourceNotFoundException("Draft not found"));
        if (conversation.createdTickets().containsKey(draftId)) {
            return "redirect:/assistant#latest";
        }
        try {
            var id = assistantService.createTicket(draft, authentication.getPrincipal());
            conversation.markCreated(draftId, id);
            conversation.add(ChatTurn.info("Ticket #" + id + " \"" + draft.title()
                    + "\" has been created. Open it from the draft above to follow its progress."));
        } catch (IllegalArgumentException e) {
            conversation.add(ChatTurn.failure("The ticket was not created: its category is no longer active."
                    + " Describe the problem again and I will prepare a new draft."));
        }
        return "redirect:/assistant#latest";
    }

    private void populate(Model model, Conversation conversation, Authentication authentication) {
        model.addAttribute("turns", conversation.turns());
        model.addAttribute("pendingReplies", conversation.pending());
        model.addAttribute("createdTickets", conversation.createdTickets());
        model.addAttribute("canCreateTickets", assistantService.canCreateTickets(authentication.getPrincipal()));
    }

    @PostMapping("/clear")
    String clear(HttpSession session) {
        Conversation.clear(session);
        return "redirect:/assistant";
    }
}
