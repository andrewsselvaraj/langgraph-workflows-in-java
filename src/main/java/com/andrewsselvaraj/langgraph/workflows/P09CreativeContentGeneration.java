package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

/**
 * project-09-creative-content-generation: a {@code generate <-> refine} cycle. Python relied on
 * {@code recursion_limit=3} to stop the cycle (which raises an error); here a round counter and a
 * conditional edge end it cleanly.
 */
@Component
public class P09CreativeContentGeneration extends AbstractWorkflow {

    public P09CreativeContentGeneration(ChatModel llm) {
        super(llm, "09", "creative-content-generation", "Slogan generator with a refine cycle",
                "Generates a marketing slogan and refines it, cycling for a given number of rounds.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("generate", node_async(state -> Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(
                        ask("Generate a marketing slogan for " + state.text("product") + ". Reply with the slogan only."))))))
                .addNode("refine", node_async(state -> Map.of(
                        GraphState.MESSAGES, List.of(new AssistantMessage(
                                ask("Refine this slogan. Reply with the slogan only: " + state.lastText()))),
                        "round", state.<Integer>value("round").orElse(0) + 1)))
                .addEdge(START, "generate")
                .addEdge("generate", "refine")
                .addConditionalEdges("refine",
                        edge_async(state -> state.<Integer>value("round").orElse(0)
                                < state.<Integer>value("rounds").orElse(1) ? "generate" : END),
                        Map.of("generate", "generate", END, END));
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("product", "a new coffee shop", "rounds", 1);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(
                "product", str(input, "product", "a new coffee shop"),
                "rounds", Integer.parseInt(str(input, "rounds", "1"))));
        return Map.of("slogan", state.lastText(),
                "history", state.messages().stream().map(GraphState::textOf).toList());
    }
}
