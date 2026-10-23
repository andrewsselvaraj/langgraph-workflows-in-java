package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;

/** project-12-email-response-automation: {@code parse -> write}. */
@Component
public class P12EmailResponseAutomation extends AbstractWorkflow {

    static final String SAMPLE_EMAIL = "The customer is asking for a refund.";

    public P12EmailResponseAutomation(ChatModel llm) {
        super(llm, "12", "email-response-automation", "Email parser and responder",
                "Parses an incoming email and drafts a reply.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("parse", node_async(state -> Map.of("email_content", state.text("email").strip())))
                .addNode("write", node_async(state -> Map.of(GraphState.MESSAGES, List.of(
                        AiMessage.from(ask("Write a response to this email: " + state.text("email_content")))))))
                .addEdge(START, "parse")
                .addEdge("parse", "write")
                .addEdge("write", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("email", SAMPLE_EMAIL);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("email", str(input, "email", SAMPLE_EMAIL)));
        return Map.of("reply", state.lastText());
    }
}
