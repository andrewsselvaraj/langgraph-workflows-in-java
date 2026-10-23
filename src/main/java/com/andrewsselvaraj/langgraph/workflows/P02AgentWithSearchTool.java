package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.ConsoleWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.andrewsselvaraj.langgraph.core.WebSearch;

/**
 * project-02-agent-with-search-tool (tavily_search.py): a ReAct loop
 * {@code agent -> tools -> agent ... -> END}. The Spring AI {@code @Tool} below is bound to the
 * model; the model only returns tool calls and the graph's {@code tools} node executes them
 * (the LangGraph {@code ToolNode}). An in-memory checkpointer keeps each {@code threadId}'s
 * conversation.
 */
@Component
public class P02AgentWithSearchTool extends AbstractWorkflow implements ConsoleWorkflow {

    private static final String SYSTEM_PROMPT =
            "You are a powerful research assistant. Your only job is to use the 'web_search' tool to answer questions. "
            + "You must not rely on your internal knowledge. You must use the tool even if you think you know the answer.";

    /** The tools the LLM may call. */
    public static class SearchTools {
        private final WebSearch webSearch;

        SearchTools(WebSearch webSearch) {
            this.webSearch = webSearch;
        }

        @Tool(name = "web_search", description = "Search the web for up-to-date information. Returns titles, URLs and snippets.")
        public String webSearch(@ToolParam(description = "The search query") String query) {
            return webSearch.search(query);
        }
    }

    private final Map<String, ToolCallback> tools;

    public P02AgentWithSearchTool(ChatModel llm, WebSearch webSearch) {
        super(llm, "02", "agent-with-search-tool", "Agent with a web search tool",
                "LLM with a bound web_search tool, a tool node and a conditional edge that loops until no more "
                + "tool calls are requested. Conversation memory per threadId via MemorySaver.");
        this.tools = Arrays.stream(ToolCallbacks.from(new SearchTools(webSearch)))
                .collect(Collectors.toMap(t -> t.getToolDefinition().name(), Function.identity()));
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("agent", node_async(this::agent))
                .addNode("tools", node_async(this::callTools))
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
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(state.messages());
        Prompt prompt = new Prompt(messages, ToolCallingChatOptions.builder()
                .toolCallbacks(List.copyOf(tools.values()))
                .build());
        return Map.of(GraphState.MESSAGES, List.of(llm.call(prompt).getResult().getOutput()));
    }

    private Map<String, Object> callTools(GraphState state) {
        AssistantMessage last = (AssistantMessage) state.lastMessage().orElseThrow();
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall call : last.getToolCalls()) {
            ToolCallback tool = tools.get(call.name());
            String output = tool == null ? "Unknown tool: " + call.name() : tool.call(call.arguments());
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), output));
        }
        return Map.of(GraphState.MESSAGES, List.of(ToolResponseMessage.builder().responses(responses).build()));
    }

    static String shouldContinue(GraphState state) {
        return state.lastMessage()
                .filter(m -> m instanceof AssistantMessage ai && ai.hasToolCalls())
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
        GraphState state = invoke(Map.of(GraphState.MESSAGES, List.of(new UserMessage(str(input, "message", "")))), config);
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
