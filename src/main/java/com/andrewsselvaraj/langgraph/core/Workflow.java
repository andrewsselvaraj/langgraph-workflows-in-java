package com.andrewsselvaraj.langgraph.core;

import java.util.Map;

/** One ported LangGraph project. */
public interface Workflow {

    /** Project number from the Python repo, e.g. {@code "01"}. */
    String id();

    /** Short name, e.g. {@code "basic-chatbot"}. */
    String slug();

    String title();

    String description();

    /** Input used when the workflow runs without arguments (the Python demo values). */
    Map<String, Object> sampleInput();

    /** Runs the graph and returns the interesting parts of the final state. */
    Map<String, Object> run(Map<String, Object> input) throws Exception;

    /** Mermaid diagram of the graph. */
    String mermaid() throws Exception;

    default String key() {
        return id() + "-" + slug();
    }
}
