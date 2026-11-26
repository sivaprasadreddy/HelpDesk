package dev.sivalabs.helpdesk.assistant;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.tickets.TicketPriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tools the chat model may call to prepare tickets for the signed-in user. Made for one question, and offered only
 * to users who may create tickets. The model can only propose a ticket ({@link TicketDraft}); the user creates it.
 * Bad input is reported back to the model as text, so it can ask the user to fix it.
 */
class TicketTools {
    private final CategoryAPI categoryAPI;
    private final List<TicketDraft> drafts = new ArrayList<>();

    TicketTools(CategoryAPI categoryAPI) {
        this.categoryAPI = categoryAPI;
    }

    record CategoryOption(Long id, String name, String description) {}

    @Tool(description = "List the active ticket categories, with the id to pass to proposeTicket.")
    List<CategoryOption> listCategories() {
        return categoryAPI.findActiveCategories().stream()
                .map(c -> new CategoryOption(c.id(), c.name(), c.description()))
                .toList();
    }

    @Tool(description = """
            Show the user a draft maintenance ticket to confirm. The ticket is created only when the user presses the
            Create ticket button under the draft; this tool does not create it. Returns what to fix if the draft is
            not valid.""")
    String proposeTicket(
            @ToolParam(description = "Short summary of the problem, at most 200 characters") String title,
            @ToolParam(description = "What is wrong and where: block, flat and floor; at most 5000 characters")
                    String description,
            @ToolParam(description = "One of LOW, MEDIUM, HIGH, CRITICAL") String priority,
            @ToolParam(description = "Id of an active category from listCategories") Long categoryId) {
        if (title == null || title.isBlank() || title.strip().length() > 200) {
            return "Not proposed: the title is required and must be at most 200 characters.";
        }
        if (description == null || description.isBlank() || description.strip().length() > 5000) {
            return "Not proposed: the description is required and must be at most 5000 characters.";
        }
        TicketPriority parsed;
        try {
            parsed = TicketPriority.valueOf(String.valueOf(priority).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "Not proposed: the priority must be one of LOW, MEDIUM, HIGH, CRITICAL.";
        }
        var category = categoryAPI.findActiveCategories().stream()
                .filter(c -> c.id().equals(categoryId))
                .findFirst();
        if (category.isEmpty()) {
            return "Not proposed: " + categoryId + " is not an active category id. Choose one from listCategories.";
        }
        drafts.add(new TicketDraft(
                UUID.randomUUID().toString(),
                title.strip(),
                description.strip(),
                parsed,
                categoryId,
                category.get().name()));
        return "The draft is shown to the user with a Create ticket button. It is NOT created yet: tell the user to"
                + " check it and press Create ticket.";
    }

    List<TicketDraft> drafts() {
        return List.copyOf(drafts);
    }
}
