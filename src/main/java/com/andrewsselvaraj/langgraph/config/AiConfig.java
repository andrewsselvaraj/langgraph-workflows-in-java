package com.andrewsselvaraj.langgraph.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * The chat model is Spring AI's auto-configured {@code OpenAiChatModel} (see {@code spring.ai.openai.*});
 * Cerebras and Ollama both speak the OpenAI API, so it replaces ChatCerebras / ChatOllama.
 */
@Configuration
public class AiConfig {

    /**
     * Local all-MiniLM-L6-v2 embeddings run in-process with ONNX (the Python projects used
     * {@code HuggingFaceEmbeddings(model_name="all-MiniLM-L6-v2")}). Lazy, because the model is
     * downloaded and cached on first use.
     */
    @Bean
    @Lazy
    public EmbeddingModel embeddingModel() {
        return new TransformersEmbeddingModel();
    }
}
