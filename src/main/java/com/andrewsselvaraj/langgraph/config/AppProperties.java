package com.andrewsselvaraj.langgraph.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All settings live under {@code app.*} in application.yml and can be overridden
 * with environment variables or a local {@code .env} file.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Llm llm,
        Tavily tavily,
        Wordpress wordpress,
        Github github,
        Shell shell,
        @DefaultValue("output") Path outputDir) {

    /**
     * Chat model. Cerebras and Ollama both speak the OpenAI API, so one
     * LangChain4j OpenAI client covers both (the Python code used
     * ChatCerebras / ChatOllama).
     */
    public record Llm(
            @DefaultValue("cerebras") String provider,
            String apiKey,
            @DefaultValue("gpt-oss-120b") String model,
            String baseUrl,
            Double temperature,
            @DefaultValue("120") int timeoutSeconds,
            @DefaultValue("false") boolean logRequests) {

        public String resolvedBaseUrl() {
            if (baseUrl != null && !baseUrl.isBlank()) {
                return baseUrl;
            }
            return switch (provider.toLowerCase()) {
                case "ollama" -> "http://localhost:11434/v1";
                case "openai" -> "https://api.openai.com/v1";
                default -> "https://api.cerebras.ai/v1";
            };
        }

        public String resolvedApiKey() {
            if (apiKey != null && !apiKey.isBlank()) {
                return apiKey;
            }
            // Ollama ignores the key, but the OpenAI client requires one.
            return "ollama".equalsIgnoreCase(provider) ? "ollama" : "missing-api-key";
        }
    }

    public record Tavily(String apiKey, @DefaultValue("3") int maxResults) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Wordpress(String url, String username, String password) {
        public boolean configured() {
            return notBlank(url) && notBlank(username) && notBlank(password);
        }
    }

    public record Github(String token, @DefaultValue("coding-agent-output") String repoName) {
        public boolean configured() {
            return notBlank(token);
        }
    }

    /** Project 19 only runs commands from this allow-list. */
    public record Shell(@DefaultValue({"ls -l", "dir", "git status", "java -version"}) List<String> allowedCommands) {
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
