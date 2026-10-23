package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * project-05-multi-agent-collaboration: the chatbot keeps talking to itself until the
 * conversation has more than five messages (a conditional edge on a numeric threshold).
 */
@Component
public class P05MultiAgentCollaboration extends AbstractWorkflow {

    static final int THRESHOLD = 5;

    public P05MultiAgentCollaboration(ChatModel llm) {
        super(llm, "05", "multi-agent-collaboration", "Conditional loop on a threshold",
                "A chatbot node loops back to itself until the message count passes a threshold, then ends.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("chatbot", node_async(state ->
                        Map.of(GraphState.MESSAGES, List.of(llm.chat(state.messages()).aiMessage()))))
                .addNode("end", node_async(state -> Map.of()))
                .addEdge(START, "chatbot")
                .addConditionalEdges("chatbot",
                        edge_async(state -> state.messages().size() > THRESHOLD ? "end" : "chatbot"),
                        Map.of("end", "end", "chatbot", "chatbot"))
                .addEdge("end", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("message", "Hello!");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(GraphState.MESSAGES, List.of(UserMessage.from(str(input, "message", "Hello!")))));
        return Map.of("answer", state.lastText(), "messageCount", state.messages().size());
    }
}
