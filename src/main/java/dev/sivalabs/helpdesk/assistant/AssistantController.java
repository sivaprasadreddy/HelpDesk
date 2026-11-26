package dev.sivalabs.helpdesk.assistant;

import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The assistant's chat page, for every signed-in user; ADMIN and USER can also raise tickets through it. */
@Controller
@RequestMapping("/assistant")
class AssistantController {
    private static final String VIEW = "assistant/chat";
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

    @PostMapping
    String ask(
            Authentication authentication,
            HttpSession session,
            @Valid @ModelAttribute("form") AskForm form,
            BindingResult bindingResult,
            Model model) {
        var conversation = Conversation.of(session);
        if (bindingResult.hasErrors()) {
            populate(model, conversation, authentication);
            return VIEW;
        }
        var question = form.message().strip();
        var history = conversation.turns();
        conversation.add(ChatTurn.user(question));
        try {
            var reply = assistantService.ask(history, question, authentication.getPrincipal());
            conversation.add(ChatTurn.assistant(reply));
        } catch (AssistantUnavailableException e) {
            conversation.add(
                    ChatTurn.failure(
                            "Sorry, I can't answer right now because the AI service is not available. Please try again later."));
        }
        return "redirect:/assistant#latest";
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
        model.addAttribute("createdTickets", conversation.createdTickets());
        model.addAttribute("canCreateTickets", assistantService.canCreateTickets(authentication.getPrincipal()));
    }

    @PostMapping("/clear")
    String clear(HttpSession session) {
        Conversation.clear(session);
        return "redirect:/assistant";
    }
}
