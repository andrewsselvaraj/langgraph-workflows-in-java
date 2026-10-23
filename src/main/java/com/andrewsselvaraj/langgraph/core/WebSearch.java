package com.andrewsselvaraj.langgraph.core;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.andrewsselvaraj.langgraph.config.AppProperties;

/**
 * Tavily web search over its REST API (the Python projects used TavilySearch / TavilySearchResults
 * and, in a couple of places, DuckDuckGo).
 */
@Component
public class WebSearch {

    private final AppProperties.Tavily props;
    private final RestClient http;

    public WebSearch(AppProperties props, RestClient.Builder builder) {
        this.props = props.tavily();
        this.http = builder.baseUrl("https://api.tavily.com").build();
    }

    public boolean isEnabled() {
        return props.enabled();
    }

    public String search(String query) {
        return search(query, props.maxResults());
    }

    public String search(String query, int maxResults) {
        if (!isEnabled()) {
            throw new IllegalStateException("This workflow needs web search: set TAVILY_API_KEY");
        }
        TavilyResponse response = http.post().uri("/search")
                .header("Authorization", "Bearer " + props.apiKey())
                .body(Map.of("query", query, "max_results", maxResults))
                .retrieve()
                .body(TavilyResponse.class);
        if (response == null || response.results() == null) {
            return "";
        }
        return response.results().stream()
                .map(r -> "Title: " + r.title() + "\nURL: " + r.url() + "\n" + r.content())
                .collect(Collectors.joining("\n\n"));
    }

    record TavilyResponse(List<Result> results) {
    }

    record Result(String title, String url, String content) {
    }
}
