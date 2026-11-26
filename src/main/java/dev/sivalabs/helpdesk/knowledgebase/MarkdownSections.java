package dev.sivalabs.helpdesk.knowledgebase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;

/**
 * Splits a Markdown document into one {@link Document} per {@code ##} section, so each answer is retrieved with the
 * heading that gives it context. Text before the first section heading is ignored unless there is no section.
 */
final class MarkdownSections {
    static final String SOURCE = "source";
    static final String DOCUMENT_TITLE = "documentTitle";
    static final String SECTION = "section";

    private MarkdownSections() {}

    static List<Document> split(String source, String markdown) {
        String documentTitle = source;
        String section = null;
        var body = new StringBuilder();
        var documents = new ArrayList<Document>();
        for (var line : markdown.split("\\R")) {
            if (line.startsWith("# ")) {
                documentTitle = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                add(documents, source, documentTitle, section, body);
                section = line.substring(3).trim();
                body.setLength(0);
            } else if (section != null) {
                body.append(line).append('\n');
            }
        }
        add(documents, source, documentTitle, section, body);
        if (documents.isEmpty() && !markdown.isBlank()) {
            documents.add(new Document(
                    markdown.strip(), Map.of(SOURCE, source, DOCUMENT_TITLE, documentTitle, SECTION, documentTitle)));
        }
        return documents;
    }

    private static void add(
            List<Document> documents, String source, String documentTitle, String section, StringBuilder body) {
        if (section == null || body.toString().isBlank()) {
            return;
        }
        var text = documentTitle + " - " + section + "\n\n" + body.toString().strip();
        documents.add(new Document(text, Map.of(SOURCE, source, DOCUMENT_TITLE, documentTitle, SECTION, section)));
    }
}
