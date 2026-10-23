package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

/** project-14-budget-data-summarization: {@code parse_csv -> summarize_budget}. */
@Component
public class P14BudgetDataSummarization extends AbstractWorkflow {

    static final String SAMPLE_CSV = """
            Category,Amount
            Food,200
            Rent,1000
            Transport,50
            Entertainment,150
            """;

    public P14BudgetDataSummarization(ChatModel llm) {
        super(llm, "14", "budget-data-summarization", "Budget planner",
                "Totals a Category,Amount CSV per category, then asks the LLM to summarise the budget.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("parse_csv", node_async(state -> Map.of("summary", totalsByCategory(state.text("csv_data")))))
                .addNode("summarize_budget", node_async(state -> Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(
                        ask("Summarize the following budget data:\n\n" + state.text("summary")))))))
                .addEdge(START, "parse_csv")
                .addEdge("parse_csv", "summarize_budget")
                .addEdge("summarize_budget", END);
    }

    /** {@code df.groupby("Category")["Amount"].sum().to_string()} */
    static String totalsByCategory(String csv) {
        List<String> lines = csv.strip().lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        List<String> header = List.of(lines.getFirst().split(","));
        int category = header.indexOf("Category");
        int amount = header.indexOf("Amount");
        if (category < 0 || amount < 0) {
            throw new IllegalArgumentException("CSV needs 'Category' and 'Amount' columns");
        }
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",");
            totals.merge(cells[category].strip(), new BigDecimal(cells[amount].strip()), BigDecimal::add);
        }
        return "Category\n" + totals.entrySet().stream()
                .map(e -> String.format("%-15s %s", e.getKey(), e.getValue().toPlainString()))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("csv_data", SAMPLE_CSV);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("csv_data", str(input, "csv_data", SAMPLE_CSV)));
        return Map.of("totals", state.text("summary"), "summary", state.lastText());
    }
}
