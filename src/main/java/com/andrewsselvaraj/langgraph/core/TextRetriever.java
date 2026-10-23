package com.andrewsselvaraj.langgraph.core;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * Tiny in-memory vector store, the Spring AI version of
 * {@code CharacterTextSplitter(chunk_size=1000) + FAISS.from_texts(...) + as_retriever()}.
 */
public final class TextRetriever {

    private final SimpleVectorStore store;

    private TextRetriever(SimpleVectorStore store) {
        this.store = store;
    }

    public static TextRetriever of(EmbeddingModel embeddingModel, String text) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        store.add(new TokenTextSplitter().apply(List.of(new Document(text))));
        return new TextRetriever(store);
    }

    /** Relevant chunks joined with newlines. */
    public String retrieve(String query) {
        return store.similaritySearch(SearchRequest.builder().query(query).topK(4).build()).stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
    }
}
