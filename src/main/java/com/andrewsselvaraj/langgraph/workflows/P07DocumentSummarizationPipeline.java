package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

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
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * project-07-document-summarization-pipeline: a checkpointer (SqliteSaver(":memory:") in Python,
 * {@link MemorySaver} here) keeps the thread's state, so asking the same question twice in one
 * thread builds on the earlier answer.
 */
@Component
public class P07DocumentSummarizationPipeline extends AbstractWorkflow {

    public P07DocumentSummarizationPipeline(ChatModel llm) {
        super(llm, "07", "document-summarization-pipeline", "Checkpointed conversation",
                "Same chatbot graph compiled with a checkpointer; repeated calls on one threadId share state.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("chatbot", node_async(state ->
                        Map.of(GraphState.MESSAGES, List.of(llm.chat(state.messages()).aiMessage()))))
                .addEdge(START, "chatbot")
                .addEdge("chatbot", END);
    }

    @Override
    protected CompileConfig compileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .releaseThread(false) // keep the thread's checkpoints after a run so the next call continues it
                .build();
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("message", "What is the capital of France?", "repeat", 2);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        String threadId = str(input, "threadId", UUID.randomUUID().toString());
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        int repeat = Integer.parseInt(str(input, "repeat", "1"));
        GraphState state = null;
        for (int i = 0; i < Math.max(1, repeat); i++) {
            state = invoke(Map.of(GraphState.MESSAGES, List.of(UserMessage.from(str(input, "message", "")))), config);
        }
        return Map.of("answer", state.lastText(), "threadId", threadId, "messagesInThread", state.messages().size());
    }
}
