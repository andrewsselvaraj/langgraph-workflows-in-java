package com.andrewsselvaraj.langgraph.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/** Common plumbing: lazy compilation, invocation and a couple of small helpers. */
public abstract class AbstractWorkflow implements Workflow {

    /**
     * Graph nodes run on these threads. Their context class loader is the application's, so classpath
     * lookups made inside nodes (Spring AI resources, the ONNX embedding model, bundled PDFs) also work
     * from the packaged jar, where the default ForkJoin threads cannot see the nested jars.
     */
    private static final ExecutorService NODE_THREADS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "graph-node");
        thread.setDaemon(true);
        thread.setContextClassLoader(AbstractWorkflow.class.getClassLoader());
        return thread;
    });

    protected final ChatModel llm;
    private final String id;
    private final String slug;
    private final String title;
    private final String description;
    private volatile CompiledGraph<GraphState> compiled;

    protected AbstractWorkflow(ChatModel llm, String id, String slug, String title, String description) {
        this.llm = llm;
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.description = description;
    }

    /** Builds the {@link StateGraph}: the equivalent of the Python {@code builder.add_node(...)} block. */
    protected abstract StateGraph<GraphState> buildGraph() throws GraphStateException;

    /** Override to add a checkpointer, interrupts or a recursion limit. */
    protected CompileConfig compileConfig() {
        return CompileConfig.builder().build();
    }

    protected CompiledGraph<GraphState> graph() throws GraphStateException {
        if (compiled == null) {
            synchronized (this) {
                if (compiled == null) {
                    compiled = buildGraph().compile(compileConfig());
                }
            }
        }
        return compiled;
    }

    protected GraphState invoke(Map<String, Object> input) throws GraphStateException {
        return invoke(input, RunnableConfig.builder().build());
    }

    protected GraphState invoke(Map<String, Object> input, RunnableConfig config) throws GraphStateException {
        return invoke(GraphInput.args(input), config);
    }

    protected GraphState invoke(GraphInput input, RunnableConfig config) throws GraphStateException {
        RunnableConfig onNodeThreads = RunnableConfig.builder(config).emitterExecutor(NODE_THREADS).build();
        return graph().invoke(input, onNodeThreads).orElseThrow(() -> new IllegalStateException("Graph produced no state"));
    }

    /** {@code llm.invoke(prompt).content} */
    protected String ask(String prompt) {
        return llm.call(prompt);
    }

    /** {@code llm.invoke(state["messages"])}: the assistant's reply to a conversation. */
    protected AssistantMessage reply(List<Message> messages) {
        return llm.call(new Prompt(messages)).getResult().getOutput();
    }

    /** A node update that appends one assistant message. */
    protected static Map<String, Object> assistant(String text) {
        return Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(text)));
    }

    protected static String str(Map<String, Object> input, String key, String fallback) {
        Object v = input.get(key);
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }

    @Override
    public String mermaid() throws GraphStateException {
        return buildGraph().getGraph(GraphRepresentation.Type.MERMAID, title, false).content();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String slug() {
        return slug;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String description() {
        return description;
    }
}
