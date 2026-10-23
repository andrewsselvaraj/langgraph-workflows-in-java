package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.andrewsselvaraj.langgraph.core.WebSearch;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * project-08-data-extraction-agent: {@code tool -> chatbot}. The tool node fetches live data with
 * Tavily and the chatbot extracts the answer from it. (In the Python file the tool node was never
 * reachable; here it runs first, and is skipped when no Tavily key is set.)
 */
@Component
public class P08DataExtractionAgent extends AbstractWorkflow {

    private final WebSearch webSearch;

    public P08DataExtractionAgent(ChatModel llm, WebSearch webSearch) {
        super(llm, "08", "data-extraction-agent", "Finance data extraction",
                "Searches the web for live data (e.g. a stock price) and extracts the answer with the LLM.");
        this.webSearch = webSearch;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("tool", node_async(state -> Map.of("research_data",
                        webSearch.isEnabled() ? webSearch.search(state.lastUserText(), 2) : "")))
                .addNode("chatbot", node_async(state -> {
                    List<ChatMessage> messages = new ArrayList<>();
                    if (!state.text("research_data").isBlank()) {
                        messages.add(SystemMessage.from("Extract the requested data from these search results "
                                + "and cite the source URL:\n\n" + state.text("research_data")));
                    }
                    messages.addAll(state.messages());
                    return Map.of(GraphState.MESSAGES, List.of(llm.chat(messages).aiMessage()));
                }))
                .addEdge(START, "tool")
                .addEdge("tool", "chatbot")
                .addEdge("chatbot", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("message", "What is the stock price of Apple?");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(GraphState.MESSAGES, List.of(UserMessage.from(str(input, "message", "")))));
        return Map.of("answer", state.lastText(), "usedWebSearch", !state.text("research_data").isBlank());
    }
}
