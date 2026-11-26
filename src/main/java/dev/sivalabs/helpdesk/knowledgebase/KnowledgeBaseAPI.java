package dev.sivalabs.helpdesk.knowledgebase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the community documents into the vector store and finds the sections that best match a question. Loading
 * needs the embedding model; when it is unreachable the application still starts, and the knowledge base stays
 * empty until the next start.
 */
@Component
public class KnowledgeBaseAPI {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeBaseAPI.class);

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resources;
    private final KnowledgeBaseProperties properties;
    private final AtomicInteger sectionCount = new AtomicInteger();

    KnowledgeBaseAPI(VectorStore vectorStore, ResourcePatternResolver resources, KnowledgeBaseProperties properties) {
        this.vectorStore = vectorStore;
        this.resources = resources;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (!properties.loadOnStartup()) {
            LOG.info("Knowledge base loading on startup is disabled");
            return;
        }
        try {
            load();
        } catch (RuntimeException e) {
            LOG.error("Could not load the knowledge base; questions will not be answered from it", e);
        }
    }

    /**
     * Reads every document matching the configured location and adds its sections to the vector store.
     *
     * @return the number of sections added
     */
    public int load() {
        var documents = new ArrayList<Document>();
        var found = findDocuments();
        for (var resource : found) {
            documents.addAll(MarkdownSections.split(resource.getFilename(), read(resource)));
        }
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        sectionCount.addAndGet(documents.size());
        LOG.info("Knowledge base loaded: {} sections from {} documents", documents.size(), found.size());
        return documents.size();
    }

    /** Whether any sections have been loaded. */
    public boolean isReady() {
        return sectionCount.get() > 0;
    }

    /** The sections most similar to the question, best first. */
    public List<KnowledgeSnippet> search(String question, int topK) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        var request = SearchRequest.builder().query(question).topK(topK).build();
        return vectorStore.similaritySearch(request).stream()
                .map(d -> new KnowledgeSnippet(
                        String.valueOf(d.getMetadata().get(MarkdownSections.SOURCE)),
                        String.valueOf(d.getMetadata().get(MarkdownSections.DOCUMENT_TITLE)),
                        String.valueOf(d.getMetadata().get(MarkdownSections.SECTION)),
                        d.getText(),
                        d.getScore() == null ? 0 : d.getScore()))
                .toList();
    }

    private List<Resource> findDocuments() {
        try {
            return Arrays.stream(resources.getResources(properties.location()))
                    .filter(Resource::isReadable)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list knowledge base documents at " + properties.location(), e);
        }
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read knowledge base document " + resource.getFilename(), e);
        }
    }
}
