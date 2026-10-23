package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.config.AppProperties;
import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.model.chat.ChatModel;

/**
 * project-23-coding-agent: five agents in a line, {@code prd -> tasks -> code -> review -> deploy}.
 * Deploy always saves the files under {@code output/coding-agent/<slug>_<timestamp>/} and also pushes
 * {@code index.html} to a new GitHub repository when {@code GITHUB_TOKEN} is set.
 */
@Component
public class P23CodingAgent extends AbstractWorkflow {

    private static final Logger log = LoggerFactory.getLogger(P23CodingAgent.class);
    private static final Pattern CODE_BLOCK = Pattern.compile("```(\\w+)\\n(.*?)```", Pattern.DOTALL);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path outputDir;
    private final GitHubPublisher gitHub;

    public P23CodingAgent(ChatModel llm, AppProperties props, GitHubPublisher gitHub) {
        super(llm, "23", "coding-agent", "Coding agent (PRD to deploy)",
                "Writes a PRD, breaks it into tasks, generates HTML/CSS/JS, reviews it and saves or deploys it.");
        this.outputDir = props.outputDir().resolve("coding-agent");
        this.gitHub = gitHub;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("prd", node_async(state -> Map.of("prd", ask("Create PRD for: " + state.text("requirement")))))
                .addNode("tasks", node_async(state -> Map.of("tasks",
                        new ArrayList<>(Arrays.asList(ask("Break this PRD into tasks: " + state.text("prd")).split("\n"))))))
                .addNode("code", node_async(state -> Map.of("code",
                        ask("Write HTML/CSS code for tasks: " + String.join("\n", state.<String>list("tasks"))))))
                .addNode("review", node_async(state -> Map.of("review", ask("Review this code for bugs: " + state.text("code")))))
                .addNode("deploy", node_async(this::deploy))
                .addEdge(START, "prd")
                .addEdge("prd", "tasks")
                .addEdge("tasks", "code")
                .addEdge("code", "review")
                .addEdge("review", "deploy")
                .addEdge("deploy", END);
    }

    private Map<String, Object> deploy(GraphState state) throws IOException {
        String slug = state.text("requirement").toLowerCase().replaceAll("[^a-z0-9]+", "_");
        slug = slug.substring(0, Math.min(30, slug.length()));
        Path dir = outputDir.resolve(slug + "_" + LocalDateTime.now().format(STAMP));
        Files.createDirectories(dir);

        Map<String, String> files = extractCodeBlocks(state.text("code"));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(dir.resolve(file.getKey()), file.getValue());
        }
        Files.writeString(dir.resolve("full_output.md"), """
                # Coding Agent Output

                **Generated:** %s

                **Requirement:** %s

                ---

                ## PRD

                %s

                ---

                ## Tasks

                %s

                ---

                ## Generated Code

                %s

                ---

                ## Code Review

                %s
                """.formatted(LocalDateTime.now(), state.text("requirement"), state.text("prd"),
                String.join("\n", state.<String>list("tasks")), state.text("code"), state.text("review")));
        log.info("Saved coding agent output to {}", dir.toAbsolutePath());

        String deployedUrl = dir.toAbsolutePath().toUri().toString();
        if (gitHub.isConfigured()) {
            try {
                deployedUrl = gitHub.publish(files.getOrDefault("index.html", state.text("code")));
            } catch (Exception e) {
                log.warn("GitHub deploy failed, files saved locally instead: {}", e.getMessage());
            }
        }
        return Map.of("deployed_url", deployedUrl);
    }

    /** html / css / js fenced blocks become index.html, styles.css and script.js. */
    static Map<String, String> extractCodeBlocks(String content) {
        Map<String, String> files = new LinkedHashMap<>();
        Matcher m = CODE_BLOCK.matcher(content);
        while (m.find()) {
            String name = switch (m.group(1).toLowerCase()) {
                case "html" -> "index.html";
                case "css" -> "styles.css";
                case "javascript", "js" -> "script.js";
                default -> null;
            };
            if (name != null) {
                files.put(name, m.group(2).strip());
            }
        }
        return files;
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("requirement", "calculator html demo");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("requirement", str(input, "requirement", "calculator html demo")));
        return Map.of("deployedUrl", state.text("deployed_url"), "review", state.text("review"));
    }
}
