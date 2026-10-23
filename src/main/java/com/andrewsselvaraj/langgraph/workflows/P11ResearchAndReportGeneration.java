package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
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

/** project-11-research-and-report-generation: two agents in sequence, {@code researcher -> writer}. */
@Component
public class P11ResearchAndReportGeneration extends AbstractWorkflow {

    public P11ResearchAndReportGeneration(ChatModel llm) {
        super(llm, "11", "research-and-report-generation", "Researcher and writer agents",
                "A researcher agent gathers notes on a topic and a writer agent turns them into a blog post.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("researcher", node_async(state -> Map.of(GraphState.MESSAGES, List.of(
                        new AssistantMessage(ask("Research " + state.text("topic") + "."))))))
                .addNode("writer", node_async(state -> Map.of(GraphState.MESSAGES, List.of(
                        new AssistantMessage(ask("Write a blog post about: " + state.lastText()))))))
                .addEdge(START, "researcher")
                .addEdge("researcher", "writer")
                .addEdge("writer", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("topic", "the latest trends in AI");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("topic", str(input, "topic", "the latest trends in AI")));
        return Map.of("research", GraphState.textOf(state.messages().getFirst()), "blogPost", state.lastText());
    }
}
