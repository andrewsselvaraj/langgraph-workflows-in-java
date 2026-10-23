package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
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

/** project-16-rag-retrieval-augmented-generation-chatbot: {@code retrieve -> generate}. */
@Component
public class P16RagChatbot extends AbstractWorkflow {

    static final String KNOWLEDGE_BASE = "LangGraph is a library for building stateful, multi-actor applications "
            + "with LLMs. It is built on top of LangChain. LangGraph4j is its Java port and integrates with LangChain4j.";

    private final EmbeddingModel embeddings;

    public P16RagChatbot(ChatModel llm, @Lazy EmbeddingModel embeddings) {
        super(llm, "16", "rag-retrieval-augmented-generation-chatbot", "RAG chatbot",
                "Retrieves relevant chunks from a knowledge base and answers from them.");
        this.embeddings = embeddings;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("retrieve", node_async(state -> Map.of("context",
                        TextRetriever.of(embeddings, state.text("knowledge_base")).retrieve(state.lastUserText()))))
                .addNode("generate", node_async(state -> Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(ask(
                        "Answer the question based on the following context:\n\n" + state.text("context")
                        + "\n\nQuestion: " + state.lastUserText()))))))
                .addEdge(START, "retrieve")
                .addEdge("retrieve", "generate")
                .addEdge("generate", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("question", "What is LangGraph?", "knowledge_base", KNOWLEDGE_BASE);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(
                "knowledge_base", str(input, "knowledge_base", KNOWLEDGE_BASE),
                GraphState.MESSAGES, List.of(new UserMessage(str(input, "question", "")))));
        return Map.of("answer", state.lastText(), "context", state.text("context"));
    }
}
