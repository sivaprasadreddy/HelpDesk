/**
 * The community knowledge base: documents on the classpath under {@code knowledge-base/}, split into sections and
 * kept in a vector store so they can be retrieved by meaning to answer questions (RAG).
 */
@ApplicationModule(displayName = "Knowledge base")
package dev.sivalabs.helpdesk.knowledgebase;

import org.springframework.modulith.ApplicationModule;
