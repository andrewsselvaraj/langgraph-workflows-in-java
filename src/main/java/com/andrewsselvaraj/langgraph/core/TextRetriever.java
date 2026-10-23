package com.andrewsselvaraj.langgraph.core;

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

/**
 * Tiny in-memory vector store, the Java version of
 * {@code CharacterTextSplitter(chunk_size=1000) + FAISS.from_texts(...) + as_retriever()}.
 */
public final class TextRetriever {

    private final EmbeddingStoreContentRetriever retriever;

    private TextRetriever(EmbeddingStoreContentRetriever retriever) {
        this.retriever = retriever;
    }

    public static TextRetriever of(EmbeddingModel embeddingModel, String text) {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(1000, 0))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(Document.from(text));
        return new TextRetriever(EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(4)
                .build());
    }

    /** Relevant chunks joined with newlines. */
    public String retrieve(String query) {
        List<Content> contents = retriever.retrieve(Query.from(query));
        return String.join("\n", contents.stream().map(c -> c.textSegment().text()).toList());
    }
}
