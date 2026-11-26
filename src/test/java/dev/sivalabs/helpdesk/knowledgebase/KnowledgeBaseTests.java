package dev.sivalabs.helpdesk.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sivalabs.helpdesk.BaseIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KnowledgeBaseTests extends BaseIT {

    @Autowired
    KnowledgeBaseAPI knowledgeBase;

    @Test
    void shouldLoadTheCommunityDocumentsOnStartup() {
        assertThat(knowledgeBase.isReady()).isTrue();
    }

    @Test
    void shouldFindTheSectionThatAnswersAQuestion() {
        var results = knowledgeBase.search("What are the swimming pool timings?", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().source()).isEqualTo("amenities.md");
        assertThat(results.getFirst().section()).isEqualTo("Swimming pool");
        assertThat(results.getFirst().text()).contains("6 AM to 9 AM");
    }

    @Test
    void shouldFindRulesAboutPets() {
        assertThat(knowledgeBase.search("How many pets can I keep in my flat?", 1))
                .singleElement()
                .satisfies(s -> assertThat(s.section()).isEqualTo("Pets"));
    }

    @Test
    void shouldReturnNothingForABlankQuestion() {
        assertThat(knowledgeBase.search("  ", 3)).isEmpty();
    }
}
