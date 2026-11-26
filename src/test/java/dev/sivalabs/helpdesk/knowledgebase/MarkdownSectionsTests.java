package dev.sivalabs.helpdesk.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownSectionsTests {

    @Test
    void shouldSplitDocumentIntoOneSnippetPerSection() {
        var docs = MarkdownSections.split("rules.md", """
                # Community Rules
                Intro that is not a section.

                ## Quiet hours
                From 10 PM to 7 AM.

                ## Pets
                Up to two pets.
                ## Empty

                """);

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getText()).isEqualTo("Community Rules - Quiet hours\n\nFrom 10 PM to 7 AM.");
        assertThat(docs.get(0).getMetadata())
                .containsEntry("source", "rules.md")
                .containsEntry("documentTitle", "Community Rules")
                .containsEntry("section", "Quiet hours");
        assertThat(docs.get(1).getMetadata()).containsEntry("section", "Pets");
    }

    @Test
    void shouldKeepADocumentWithoutSectionsWhole() {
        var docs = MarkdownSections.split("note.md", "# Note\nJust one paragraph.");

        assertThat(docs).singleElement().satisfies(d -> {
            assertThat(d.getText()).isEqualTo("# Note\nJust one paragraph.");
            assertThat(d.getMetadata()).containsEntry("section", "Note");
        });
    }
}
