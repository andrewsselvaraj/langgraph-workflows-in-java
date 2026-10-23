package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.andrewsselvaraj.langgraph.core.WebSearch;

/** project-18-web-research-and-report-generation: {@code research} (Tavily) {@code -> report}. Needs TAVILY_API_KEY. */
@Component
public class P18WebResearchReport extends AbstractWorkflow {

    private final WebSearch webSearch;

    public P18WebResearchReport(ChatModel llm, WebSearch webSearch) {
        super(llm, "18", "web-research-and-report-generation", "Web research report",
                "Searches the web for a topic and compiles a report from the results.");
        this.webSearch = webSearch;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("research", node_async(state -> Map.of("research_data", webSearch.search(state.lastUserText()))))
                .addNode("report", node_async(state -> Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(ask(
                        "Generate a comprehensive report based on the following research data:\n\n"
                        + state.text("research_data")))))))
                .addEdge(START, "research")
                .addEdge("research", "report")
                .addEdge("report", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("topic", "Latest trends in renewable energy.");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(GraphState.MESSAGES, List.of(new UserMessage(str(input, "topic", "")))));
        return Map.of("report", state.lastText(), "sources", state.text("research_data"));
    }
}
