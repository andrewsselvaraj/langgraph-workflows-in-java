package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import dev.langchain4j.model.chat.ChatModel;

/**
 * project-22-blog-writing-and-publishing-agent: {@code research -> write -> proofread -> publish}.
 * Research downloads each URL and strips it to text, the writer produces an SEO blog post for the
 * organisation, the proofreader polishes it and the publisher posts it to WordPress (skipped, with
 * {@code published=false}, when no WordPress credentials are configured).
 */
@Component
public class P22BlogWritingAndPublishingAgent extends AbstractWorkflow {

    private static final Logger log = LoggerFactory.getLogger(P22BlogWritingAndPublishingAgent.class);
    private static final int MAX_RESEARCH_CHARS = 20_000;
    static final String ORGANIZATION_NAME = "HERE AND NOW AI";
    static final String SAMPLE_URL = "https://www.anthropic.com/news/model-context-protocol";

    private final WordPressClient wordPress;
    private final String organizationDescription;

    public P22BlogWritingAndPublishingAgent(ChatModel llm, WordPressClient wordPress) throws IOException {
        super(llm, "22", "blog-writing-and-publishing-agent", "Blog writer and publisher",
                "Researches URLs, writes an SEO blog post, proofreads it and publishes it to WordPress.");
        this.wordPress = wordPress;
        this.organizationDescription = new ClassPathResource("data/organization-description.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("research", node_async(this::research))
                .addNode("write", node_async(this::write))
                .addNode("proofread", node_async(this::proofread))
                .addNode("publish", node_async(this::publish))
                .addEdge(START, "research")
                .addEdge("research", "write")
                .addEdge("write", "proofread")
                .addEdge("proofread", "publish")
                .addEdge("publish", END);
    }

    private Map<String, Object> research(GraphState state) {
        StringBuilder all = new StringBuilder();
        for (String url : state.<String>list("urls")) {
            try {
                Document page = new HtmlToTextDocumentTransformer()
                        .transform(UrlDocumentLoader.load(url, new TextDocumentParser()));
                all.append("--- Content from ").append(url).append(" ---\n").append(page.text()).append("\n\n");
                log.info("Fetched content from {}", url);
            } catch (Exception e) {
                log.warn("Error fetching content from {}: {}", url, e.getMessage());
            }
        }
        String text = all.length() > MAX_RESEARCH_CHARS ? all.substring(0, MAX_RESEARCH_CHARS) : all.toString();
        return Map.of("research_results", text);
    }

    private Map<String, Object> write(GraphState state) {
        String prompt = """
                You are an expert SEO content writer for the company: %s.
                Company Description: %s.

                Your task is to write a comprehensive, engaging, and SEO-optimized blog post based on the following research content:
                %s

                Please adhere to the latest SEO best practices, including:
                - Use the main keyword phrase naturally throughout the article.
                - Include related LSI keywords.
                - Write a compelling meta title and meta description.
                - Use headings (H1, H2, H3) to structure the content.
                - Write an introduction that hooks the reader and a conclusion that summarizes the key points.
                - Ensure the article is at least 800 words long.

                The tone should be professional, informative, and engaging.
                """.formatted(state.text("organization_name"), state.text("organization_description"),
                state.text("research_results"));
        return Map.of("blog_post", ask(prompt));
    }

    private Map<String, Object> proofread(GraphState state) {
        String prompt = """
                You are an expert proofreader and editor.
                Your task is to review the following blog post for any grammatical errors, spelling mistakes, or awkward phrasing.
                Please also ensure the article is clear, concise, and easy to read.

                Blog Post:
                ---
                %s
                ---

                Return the polished, final version of the blog post as a single block of markdown text. Do not include any HTML tags or code blocks.
                """.formatted(state.text("blog_post"));
        try {
            return Map.of("final_blog_post", ask(prompt));
        } catch (RuntimeException e) {
            log.warn("Error during proofreading, keeping the draft: {}", e.getMessage());
            return Map.of("final_blog_post", state.text("blog_post"));
        }
    }

    private Map<String, Object> publish(GraphState state) {
        Post post = toPost(state.text("final_blog_post"));
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("post_title", post.title());
        if (!wordPress.isConfigured()) {
            log.info("WordPress credentials not set; skipping publish.");
            update.put("published", false);
            return update;
        }
        try {
            update.put("post_id", wordPress.newPost(post.title(), post.html()));
            update.put("published", true);
        } catch (Exception e) {
            log.warn("Error publishing to WordPress: {}", e.getMessage());
            update.put("published", false);
        }
        return update;
    }

    record Post(String title, String html) {
    }

    /** Strips code fences, takes the H1 (or the first line) as title and renders the rest to HTML. */
    static Post toPost(String markdown) {
        String cleaned = markdown.strip()
                .replaceFirst("(?i)^```(html|markdown)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .strip();
        List<String> lines = new ArrayList<>(Arrays.asList(cleaned.split("\n", -1)));
        String title = "Automated Blog Post";
        int titleLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().startsWith("# ")) {
                titleLine = i;
                break;
            }
        }
        if (titleLine < 0) {
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).isBlank()) {
                    titleLine = i;
                    break;
                }
            }
        }
        String body = cleaned;
        if (titleLine >= 0) {
            title = lines.get(titleLine).strip().replaceFirst("^#+\\s*", "");
            body = String.join("\n", lines.subList(titleLine + 1, lines.size())).strip();
        }
        String html = HtmlRenderer.builder().build().render(Parser.builder().build().parse(body));
        return new Post(title, html);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("urls", List.of(SAMPLE_URL));
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        Object rawUrls = input.getOrDefault("urls", List.of(SAMPLE_URL));
        List<String> urls = rawUrls instanceof List<?> l
                ? new ArrayList<>(l.stream().map(String::valueOf).toList())
                : new ArrayList<>(Arrays.stream(rawUrls.toString().split(",")).map(String::trim).toList());
        GraphState state = invoke(Map.of(
                "organization_name", str(input, "organization_name", ORGANIZATION_NAME),
                "organization_description", str(input, "organization_description", organizationDescription),
                "urls", urls));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("published", state.flag("published"));
        result.put("title", state.text("post_title"));
        state.value("post_id").ifPresent(id -> result.put("postId", id));
        result.put("blogPost", state.text("final_blog_post"));
        return result;
    }
}
