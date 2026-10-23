package com.andrewsselvaraj.langgraph.core;

import java.util.Map;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;

import dev.langchain4j.model.chat.ChatModel;

/** Common plumbing: lazy compilation, invocation and a couple of small helpers. */
public abstract class AbstractWorkflow implements Workflow {

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
        return graph().invoke(input).orElseThrow(() -> new IllegalStateException("Graph produced no state"));
    }

    protected GraphState invoke(Map<String, Object> input, RunnableConfig config) throws GraphStateException {
        return graph().invoke(input, config).orElseThrow(() -> new IllegalStateException("Graph produced no state"));
    }

    /** {@code llm.invoke(prompt).content} */
    protected String ask(String prompt) {
        return llm.chat(prompt);
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
