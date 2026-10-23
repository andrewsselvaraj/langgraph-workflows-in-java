package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.andrewsselvaraj.langgraph.core.TextRetriever;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * project-10-decision-making-agent (HR policy helper): {@code retrieve -> chatbot} over a small
 * policy document held in an in-memory vector store.
 */
@Component
public class P10DecisionMakingAgent extends AbstractWorkflow {

    static final String POLICY = "Our company's vacation policy allows for 20 days of paid time off per year.";

    private final EmbeddingModel embeddings;

    public P10DecisionMakingAgent(ChatModel llm, @Lazy EmbeddingModel embeddings) {
        super(llm, "10", "decision-making-agent", "HR policy helper",
                "Answers HR questions from a policy document via vector retrieval.");
        this.embeddings = embeddings;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("retrieve", node_async(state -> Map.of("context",
                        TextRetriever.of(embeddings, state.text("policy")).retrieve(state.lastUserText()))))
                .addNode("chatbot", node_async(state -> Map.of(GraphState.MESSAGES, List.of(llm.chat(
                        UserMessage.from("Answer using this company policy:\n" + state.text("context")
                                + "\n\nQuestion: " + state.lastUserText())).aiMessage()))))
                .addEdge(START, "retrieve")
                .addEdge("retrieve", "chatbot")
                .addEdge("chatbot", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("question", "How many vacation days do I get?", "policy", POLICY);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(
                "policy", str(input, "policy", POLICY),
                GraphState.MESSAGES, List.of(UserMessage.from(str(input, "question", "")))));
        return Map.of("answer", state.lastText(), "context", state.text("context"));
    }
}
