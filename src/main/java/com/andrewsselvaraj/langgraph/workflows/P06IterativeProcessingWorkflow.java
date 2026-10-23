package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

/**
 * project-06-iterative-processing-workflow: map-reduce style. {@code process} runs the LLM once per
 * item (map), {@code aggregate} reduces the results into one summary message.
 */
@Component
public class P06IterativeProcessingWorkflow extends AbstractWorkflow {

    public P06IterativeProcessingWorkflow(ChatModel llm) {
        super(llm, "06", "iterative-processing-workflow", "Map-reduce over a list of items",
                "Processes every item with the LLM, then aggregates the results.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("process", node_async(state -> {
                    List<Message> results = new ArrayList<>();
                    for (String item : state.<String>list("items")) {
                        results.add(new AssistantMessage(ask("Process this item: " + item)));
                    }
                    return Map.of(GraphState.MESSAGES, results);
                }))
                .addNode("aggregate", node_async(state -> Map.of(GraphState.MESSAGES, List.of(
                        new AssistantMessage("Aggregated results: " + state.messages().size() + " items processed")))))
                .addEdge(START, "process")
                .addEdge("process", "aggregate")
                .addEdge("aggregate", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("items", List.of("apple", "banana", "cherry"));
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("items", items(input.get("items"))));
        List<String> processed = state.messages().stream().map(GraphState::textOf).toList();
        return Map.of("summary", state.lastText(), "results", processed.subList(0, processed.size() - 1));
    }

    /** Accepts a JSON array or a comma separated string (handy on the command line). */
    private static List<String> items(Object raw) {
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list.stream().map(String::valueOf).toList());
        }
        return new ArrayList<>(Arrays.stream(String.valueOf(raw).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
}
