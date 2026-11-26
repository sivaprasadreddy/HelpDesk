package dev.sivalabs.helpdesk;

import java.util.Locale;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** AI models that run in the test JVM, so tests need no Ollama or OpenAI. */
@TestConfiguration(proxyBeanMethods = false)
public class TestAiConfig {

    @Bean
    @Primary
    FakeChatModel fakeChatModel() {
        return new FakeChatModel();
    }

    @Bean
    @Primary
    EmbeddingModel testEmbeddingModel() {
        return new BagOfWordsEmbeddingModel();
    }

    /**
     * Embeds text as hashed word counts, normalised to unit length: texts that share words are similar, which is
     * enough to check that retrieval finds the right section.
     */
    static final class BagOfWordsEmbeddingModel implements EmbeddingModel {
        static final int DIMENSIONS = 512;
        static final Set<String> STOP_WORDS = Set.of(
                "a", "an", "the", "is", "are", "and", "or", "of", "to", "in", "on", "for", "at", "by", "be", "can",
                "do", "does", "i", "my", "what", "when", "how", "which", "it", "with", "from", "up", "must", "not");

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            var texts = request.getInstructions();
            var embeddings = new java.util.ArrayList<Embedding>();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(new Embedding(vector(texts.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        static float[] vector(String text) {
            var v = new float[DIMENSIONS];
            for (var word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                    v[Math.floorMod(stem(word).hashCode(), DIMENSIONS)] += 1;
                }
            }
            double norm = 0;
            for (var x : v) norm += x * x;
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
            }
            return v;
        }

        private static String stem(String word) {
            return word.length() > 3 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
        }
    }
}
