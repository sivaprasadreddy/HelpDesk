package dev.sivalabs.helpdesk.assistant;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The assistant's chat page, for every signed-in user. */
@Controller
@RequestMapping("/assistant")
class AssistantController {
    private static final String VIEW = "assistant/chat";
    private final AssistantService assistantService;

    AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping
    String chat(HttpSession session, Model model) {
        model.addAttribute("turns", Conversation.of(session).turns());
        model.addAttribute("form", AskForm.empty());
        return VIEW;
    }

    @PostMapping
    String ask(
            HttpSession session,
            @Valid @ModelAttribute("form") AskForm form,
            BindingResult bindingResult,
            Model model) {
        var conversation = Conversation.of(session);
        if (bindingResult.hasErrors()) {
            model.addAttribute("turns", conversation.turns());
            return VIEW;
        }
        var question = form.message().strip();
        var history = conversation.turns();
        conversation.add(ChatTurn.user(question));
        try {
            var reply = assistantService.ask(history, question);
            conversation.add(ChatTurn.assistant(reply.text(), reply.sources()));
        } catch (AssistantUnavailableException e) {
            conversation.add(
                    ChatTurn.failure(
                            "Sorry, I can't answer right now because the AI service is not available. Please try again later."));
        }
        return "redirect:/assistant#latest";
    }

    @PostMapping("/clear")
    String clear(HttpSession session) {
        Conversation.clear(session);
        return "redirect:/assistant";
    }
}
