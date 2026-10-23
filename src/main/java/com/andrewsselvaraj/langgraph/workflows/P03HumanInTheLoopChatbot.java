package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.ConsoleWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.andrewsselvaraj.langgraph.core.WebSearch;

/**
 * project-03-human-in-the-loop-chatbot: {@code search -> draft -> approve -> final}.
 * The graph is compiled with {@code interruptBefore("approve")}, so it pauses after the draft
 * (the Python {@code interrupt(...)}); the human's answer is written into the checkpoint with
 * {@code updateState} and the run is resumed (the Python {@code Command(resume=...)}).
 *
 * <p>REST: first POST {@code {"question": "..."}} returns {@code threadId} and the draft,
 * then POST {@code {"threadId": "...", "answer": "yes"}} finishes the run.
 */
@Component
public class P03HumanInTheLoopChatbot extends AbstractWorkflow implements ConsoleWorkflow {

    private static final Set<String> YES = Set.of("yes", "y", "approve");

    private final WebSearch webSearch;

    public P03HumanInTheLoopChatbot(ChatModel llm, WebSearch webSearch) {
        super(llm, "03", "human-in-the-loop-chatbot", "Human-in-the-loop chatbot",
                "Searches the web, drafts an answer, then pauses for a human to approve it before finalising.");
        this.webSearch = webSearch;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("search", node_async(this::search))
                .addNode("draft", node_async(this::draft))
                .addNode("approve", node_async(state ->
                        Map.of("approved", YES.contains(state.text("human_answer").trim().toLowerCase()))))
                .addNode("final", node_async(state -> Map.of("draft",
                        state.flag("approved") ? state.text("draft") : "I'll refine and resend shortly.")))
                .addEdge(START, "search")
                .addEdge("search", "draft")
                .addEdge("draft", "approve")
                .addEdge("approve", "final")
                .addEdge("final", END);
    }

    @Override
    protected CompileConfig compileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .interruptBefore("approve")
                .build();
    }

    private Map<String, Object> search(GraphState state) {
        String question = state.text("question");
        String snippet = webSearch.isEnabled() ? webSearch.search(question) : "(web search disabled: set TAVILY_API_KEY)";
        return Map.of("snippet", snippet);
    }

    private Map<String, Object> draft(GraphState state) {
        String prompt = "Question: " + state.text("question") + "\n\n"
                + "Found info: " + state.text("snippet") + "\n\n"
                + "Write a factual draft answer based on the above snippet.";
        return Map.of("draft", ask(prompt));
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("question", "What is LangGraph4j?");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        Object threadId = input.get("threadId");
        if (threadId != null && input.get("answer") != null) {
            return resume(threadId.toString(), input.get("answer").toString());
        }
        return start(str(input, "question", ""));
    }

    /** Runs until the interrupt before "approve". */
    public Map<String, Object> start(String question) throws Exception {
        String threadId = UUID.randomUUID().toString();
        GraphState state = invoke(Map.of("question", question), config(threadId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "awaiting_approval");
        result.put("threadId", threadId);
        result.put("draft", state.text("draft"));
        result.put("next", "POST again with {\"threadId\": \"" + threadId + "\", \"answer\": \"yes|no\"}");
        return result;
    }

    /** Stores the human's answer in the checkpoint and resumes the graph. */
    public Map<String, Object> resume(String threadId, String answer) throws Exception {
        RunnableConfig config = graph().updateState(config(threadId), Map.of("human_answer", answer));
        GraphState state = invoke(GraphInput.resume(), config);
        return Map.of("status", "done", "threadId", threadId,
                "approved", state.flag("approved"), "answer", state.text("draft"));
    }

    private static RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }

    @Override
    public void runConsole(BufferedReader in, PrintStream out) throws Exception {
        out.print("Ask your question: ");
        Map<String, Object> pending = start(in.readLine());
        out.println("\nDraft:\n" + pending.get("draft"));
        out.print("Approve? (yes/no): ");
        Map<String, Object> done = resume((String) pending.get("threadId"), in.readLine());
        out.println("\nFinal answer: " + done.get("answer"));
    }
}
