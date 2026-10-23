package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.ConsoleWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/** project-01-basic-chatbot: START -> bot -> END, one LLM call per user message. */
@Component
public class P01BasicChatbot extends AbstractWorkflow implements ConsoleWorkflow {

    private static final Set<String> QUIT = Set.of("quit", "exit", "q");

    public P01BasicChatbot(ChatModel llm) {
        super(llm, "01", "basic-chatbot", "Basic chatbot",
                "Simplest possible StateGraph: a single 'bot' node that sends the messages to the LLM.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("bot", node_async(state ->
                        Map.of(GraphState.MESSAGES, List.of(llm.chat(state.messages()).aiMessage()))))
                .addEdge(START, "bot")
                .addEdge("bot", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("message", "Hello! What can LangGraph do?");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(GraphState.MESSAGES, List.of(UserMessage.from(str(input, "message", "Hello")))));
        return Map.of("answer", state.lastText());
    }

    @Override
    public void runConsole(BufferedReader in, PrintStream out) throws Exception {
        while (true) {
            out.print("You: ");
            String line = in.readLine();
            if (line == null || QUIT.contains(line.trim().toLowerCase())) {
                break;
            }
            out.println("AI: " + run(Map.of("message", line)).get("answer"));
        }
    }
}
