package com.andrewsselvaraj.langgraph.workflows;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Creates a GitHub repository and commits {@code index.html} to it, like the PyGithub calls in
 * project 23's deploy agent. Only used when {@code GITHUB_TOKEN} is set.
 */
@Component
public class GitHubPublisher {

    private final AppProperties.Github props;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public GitHubPublisher(AppProperties props) {
        this.props = props.github();
    }

    public boolean isConfigured() {
        return props.configured();
    }

    /** Returns the repository's html_url. */
    public String publish(String indexHtml) throws Exception {
        JsonNode repo = call("POST", "https://api.github.com/user/repos", Map.of("name", props.repoName()));
        String fullName = repo.path("full_name").asText();
        call("PUT", "https://api.github.com/repos/" + fullName + "/contents/index.html", Map.of(
                "message", "deploy",
                "content", Base64.getEncoder().encodeToString(indexHtml.getBytes(StandardCharsets.UTF_8))));
        return repo.path("html_url").asText();
    }

    private JsonNode call(String method, String url, Map<String, Object> body) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + props.token())
                        .header("Accept", "application/vnd.github+json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub " + method + " " + url + " failed: HTTP "
                    + response.statusCode() + " " + response.body());
        }
        return json.readTree(response.body());
    }
}
