package com.andrewsselvaraj.langgraph.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * App settings under {@code app.*} in application.yml (overridable with environment variables or
 * a local {@code .env}). The LLM itself is configured by Spring AI under {@code spring.ai.openai.*}.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Tavily tavily,
        Wordpress wordpress,
        Github github,
        Shell shell,
        @DefaultValue("output") Path outputDir) {

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
