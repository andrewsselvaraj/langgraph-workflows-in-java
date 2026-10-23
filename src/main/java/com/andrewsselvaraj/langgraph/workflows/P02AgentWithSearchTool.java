package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.ConsoleWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.andrewsselvaraj.langgraph.core.WebSearch;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/**
 * project-02-agent-with-search-tool (tavily_search.py): a ReAct loop
 * {@code agent -> tools -> agent ... -> END} with the Tavily search tool bound to the LLM
 * and an in-memory checkpointer so each {@code threadId} keeps its conversation.
 */
@Component
public class P02AgentWithSearchTool extends AbstractWorkflow implements ConsoleWorkflow {

    static final ToolSpecification WEB_SEARCH = ToolSpecification.builder()
            .name("web_search")
            .description("Search the web for up-to-date information. Returns titles, URLs and snippets.")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("query", "The search query")
                    .required("query")
                    .build())
            .build();

    private static final String SYSTEM_PROMPT =
            "You are a powerful research assistant. Your only job is to use the 'web_search' tool to answer questions. "
            + "You must not rely on your internal knowledge. You must use the tool even if you think you know the answer.";

    private final WebSearch webSearch;
    private final ObjectMapper json = new ObjectMapper();

    public P02AgentWithSearchTool(ChatModel llm, WebSearch webSearch) {
        super(llm, "02", "agent-with-search-tool", "Agent with a web search tool",
                "LLM with a bound web_search tool, a tool node and a conditional edge that loops until no more "
                + "tool calls are requested. Conversation memory per threadId via MemorySaver.");
        this.webSearch = webSearch;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("agent", node_async(this::agent))
                .addNode("tools", node_async(this::tools))
                .addEdge(START, "agent")
                .addConditionalEdges("agent", edge_async(P02AgentWithSearchTool::shouldContinue),
                        Map.of("tools", "tools", END, END))
                .addEdge("tools", "agent");
    }

    @Override
    protected CompileConfig compileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .releaseThread(false) // keep the thread's checkpoints after a run so the next call continues it
                .build();
    }

    private Map<String, Object> agent(GraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        messages.addAll(state.messages());
        AiMessage response = llm.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(WEB_SEARCH)
                .build()).aiMessage();
        return Map.of(GraphState.MESSAGES, List.of(response));
    }

    private Map<String, Object> tools(GraphState state) throws Exception {
        AiMessage last = (AiMessage) state.lastMessage().orElseThrow();
        List<ChatMessage> results = new ArrayList<>();
        for (ToolExecutionRequest call : last.toolExecutionRequests()) {
            String output = WEB_SEARCH.name().equals(call.name())
                    ? webSearch.search(json.readTree(call.arguments()).path("query").asText())
                    : "Unknown tool: " + call.name();
            results.add(ToolExecutionResultMessage.from(call, output));
        }
        return Map.of(GraphState.MESSAGES, results);
    }

    static String shouldContinue(GraphState state) {
        return state.lastMessage()
                .filter(m -> m instanceof AiMessage ai && ai.hasToolExecutionRequests())
                .map(m -> "tools")
                .orElse(END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("message", "What is the weather in Chennai?");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        String threadId = str(input, "threadId", UUID.randomUUID().toString());
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        GraphState state = invoke(Map.of(GraphState.MESSAGES, List.of(UserMessage.from(str(input, "message", "")))), config);
        return Map.of("answer", state.lastText(), "threadId", threadId);
    }

    @Override
    public void runConsole(BufferedReader in, PrintStream out) throws Exception {
        String threadId = "session1";
        while (true) {
            out.print("You: ");
            String line = in.readLine();
            if (line == null || line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                break;
            }
            out.println("AI: " + run(Map.of("message", line, "threadId", threadId)).get("answer"));
        }
    }
}
