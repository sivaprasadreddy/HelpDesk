package dev.sivalabs.helpdesk.knowledgebase;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param location the documents to load, as a resource pattern
 * @param loadOnStartup whether to load them into the vector store when the application starts
 */
@ConfigurationProperties(prefix = "helpdesk.knowledge-base")
record KnowledgeBaseProperties(
        @DefaultValue("classpath*:knowledge-base/*.md") String location,
        @DefaultValue("true") boolean loadOnStartup) {}
