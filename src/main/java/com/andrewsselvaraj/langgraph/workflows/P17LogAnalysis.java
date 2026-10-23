package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

/** project-17-log-analysis-and-summarization: {@code parse_logs} (keep ERROR lines) {@code -> summarize_errors}. */
@Component
public class P17LogAnalysis extends AbstractWorkflow {

    static final String SAMPLE_LOG = """
            ERROR: Disk full. Cannot write to /var/log/app.log
            INFO: User 'admin' logged in from 192.168.1.100
            WARNING: High CPU usage detected on server 'web-01'
            ERROR: Database connection failed. Retrying...
            """;

    public P17LogAnalysis(ChatModel llm) {
        super(llm, "17", "log-analysis-and-summarization", "Log analyzer",
                "Filters the ERROR lines out of a log and asks the LLM to summarise them.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("parse_logs", node_async(state -> Map.of("summary", errorLines(state.text("log_content")))))
                .addNode("summarize_errors", node_async(state -> Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(
                        ask("Summarize the following error logs:\n\n" + state.text("summary")))))))
                .addEdge(START, "parse_logs")
                .addEdge("parse_logs", "summarize_errors")
                .addEdge("summarize_errors", END);
    }

    static String errorLines(String log) {
        return log.lines().filter(l -> l.contains("ERROR")).collect(Collectors.joining("\n"));
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("log_content", SAMPLE_LOG);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("log_content", str(input, "log_content", SAMPLE_LOG)));
        return Map.of("errors", state.text("summary"), "summary", state.lastText());
    }
}
