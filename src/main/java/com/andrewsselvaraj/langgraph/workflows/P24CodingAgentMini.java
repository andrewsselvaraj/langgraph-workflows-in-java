package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.config.AppProperties;
import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.model.chat.ChatModel;

/** project-24-coding-agent-mini: {@code coder -> reviewer -> saver} (writes {@code output/index.html}). */
@Component
public class P24CodingAgentMini extends AbstractWorkflow {

    private final Path outputDir;

    public P24CodingAgentMini(ChatModel llm, AppProperties props) {
        super(llm, "24", "coding-agent-mini", "Mini coding agent",
                "Generates a single HTML page, reviews it and saves it to output/index.html.");
        this.outputDir = props.outputDir();
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("coder", node_async(state -> Map.of("code",
                        ask("Generate HTML with inline CSS and JS animations for: " + state.text("request")))))
                .addNode("reviewer", node_async(state -> Map.of("review",
                        ask("Review this code briefly, suggest improvements:\n" + state.text("code")))))
                .addNode("saver", node_async(state -> {
                    Files.createDirectories(outputDir);
                    Path file = outputDir.resolve("index.html");
                    Files.writeString(file, state.text("code"));
                    return Map.of("saved_to", file.toAbsolutePath().toString());
                }))
                .addEdge(START, "coder")
                .addEdge("coder", "reviewer")
                .addEdge("reviewer", "saver")
                .addEdge("saver", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("request", "a coffee shop landing page");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("request", str(input, "request", "a coffee shop landing page")));
        return Map.of("savedTo", state.text("saved_to"), "review", state.text("review"));
    }
}
