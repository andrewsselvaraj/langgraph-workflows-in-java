package com.andrewsselvaraj.langgraph.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

@Configuration
public class AiConfig {

    /** Equivalent of {@code llm = ChatCerebras(model="gpt-oss-120b")} / {@code ChatOllama(...)}. */
    @Bean
    public ChatModel chatModel(AppProperties props) {
        AppProperties.Llm llm = props.llm();
        return OpenAiChatModel.builder()
                .baseUrl(llm.resolvedBaseUrl())
                .apiKey(llm.resolvedApiKey())
                .modelName(llm.model())
                .temperature(llm.temperature())
                .timeout(Duration.ofSeconds(llm.timeoutSeconds()))
                .maxRetries(2)
                .logRequests(llm.logRequests())
                .logResponses(llm.logRequests())
                .build();
    }

    /**
     * Local all-MiniLM-L6-v2 embeddings (same model as the Python
     * {@code HuggingFaceEmbeddings(model_name="all-MiniLM-L6-v2")}), run in-process via ONNX.
     */
    @Bean
    @Lazy
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
