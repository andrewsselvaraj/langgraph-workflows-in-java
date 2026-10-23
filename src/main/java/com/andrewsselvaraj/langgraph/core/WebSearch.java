package com.andrewsselvaraj.langgraph.core;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.config.AppProperties;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;

/**
 * Tavily web search (the Python projects used TavilySearch / TavilySearchResults
 * and, in a couple of places, DuckDuckGo).
 */
@Component
public class WebSearch {

    private final AppProperties.Tavily props;
    private volatile WebSearchEngine engine;

    public WebSearch(AppProperties props) {
        this.props = props.tavily();
    }

    public boolean isEnabled() {
        return props.enabled();
    }

    public String search(String query) {
        return search(query, props.maxResults());
    }

    public String search(String query, int maxResults) {
        WebSearchResults results = engine().search(WebSearchRequest.builder()
                .searchTerms(query)
                .maxResults(maxResults)
                .build());
        return results.results().stream()
                .map(r -> "Title: " + r.title() + "\nURL: " + r.url() + "\n" + r.snippet())
                .collect(Collectors.joining("\n\n"));
    }

    public WebSearchEngine engine() {
        if (!isEnabled()) {
            throw new IllegalStateException("This workflow needs web search: set TAVILY_API_KEY");
        }
        if (engine == null) {
            engine = TavilyWebSearchEngine.builder().apiKey(props.apiKey()).build();
        }
        return engine;
    }
}
