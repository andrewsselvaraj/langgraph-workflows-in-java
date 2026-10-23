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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.andrewsselvaraj.langgraph.core.TextRetriever;

/**
 * project-20-multi-domain-routing-agent (capstone): a keyword router on START sends the question to
 * the finance, HR, marketing or general agent. (The Python version chained every specialist into
 * {@code general_chat} and used {@code route_query} as an entry point that was never added as a
 * node; here the router is a conditional entry edge and each agent ends the run.)
 */
@Component
public class P20MultiDomainRoutingAgent extends AbstractWorkflow {

    static final String HR_POLICY =
            "Our company offers 20 days of paid vacation per year and a comprehensive health insurance plan.";

    private final EmbeddingModel embeddings;

    public P20MultiDomainRoutingAgent(ChatModel llm, @Lazy EmbeddingModel embeddings) {
        super(llm, "20", "multi-domain-routing-agent", "Unified assistant (finance + HR + marketing)",
                "Routes each question to a finance, HR, marketing or general agent.");
        this.embeddings = embeddings;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("general_chat", node_async(state -> reply(reply(state.messages()).getText())))
                .addNode("finance_agent", node_async(state -> reply(ask("Summarize this budget data: "
                        + P14BudgetDataSummarization.totalsByCategory(P14BudgetDataSummarization.SAMPLE_CSV)))))
                .addNode("hr_agent", node_async(state -> {
                    String query = state.lastUserText();
                    String context = TextRetriever.of(embeddings, HR_POLICY).retrieve(query);
                    return reply(ask("Answer based on HR policy: " + context + "\nQuestion: " + query));
                }))
                .addNode("marketing_agent", node_async(state -> reply(ask("Generate a marketing slogan for a new product."))))
                .addConditionalEdges(START, edge_async(state -> route(state.lastUserText())), Map.of(
                        "finance_agent", "finance_agent",
                        "hr_agent", "hr_agent",
                        "marketing_agent", "marketing_agent",
                        "general_chat", "general_chat"))
                .addEdge("finance_agent", END)
                .addEdge("hr_agent", END)
                .addEdge("marketing_agent", END)
                .addEdge("general_chat", END);
    }

    static String route(String message) {
        String m = message.toLowerCase();
        if (m.contains("finance") || m.contains("budget")) {
            return "finance_agent";
        }
        if (m.matches(".*\\bhr\\b.*") || m.contains("policy")) {
            return "hr_agent";
        }
        if (m.contains("marketing") || m.contains("slogan")) {
            return "marketing_agent";
        }
        return "general_chat";
    }

    private static Map<String, Object> reply(String text) {
        return Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(text)));
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("message", "Can you give me a summary of my budget?");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        String message = str(input, "message", "");
        GraphState state = invoke(Map.of(GraphState.MESSAGES, List.of(new UserMessage(message))));
        return Map.of("routedTo", route(message), "answer", state.lastText());
    }
}
