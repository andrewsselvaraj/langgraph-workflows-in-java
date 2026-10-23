package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.config.AppProperties;
import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * project-19-shell-command-execution-and-summarization: {@code execute -> summarize}.
 * Because this is reachable over HTTP, only commands listed in {@code app.shell.allowed-commands} run.
 */
@Component
public class P19ShellCommandSummarization extends AbstractWorkflow {

    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private final List<String> allowedCommands;

    public P19ShellCommandSummarization(ChatModel llm, AppProperties props) {
        super(llm, "19", "shell-command-execution-and-summarization", "Command-line tool agent",
                "Runs an allow-listed shell command and asks the LLM to summarise its output.");
        this.allowedCommands = props.shell().allowedCommands();
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("execute", node_async(state -> Map.of("command_output", runCommand(state.text("command")))))
                .addNode("summarize", node_async(state -> Map.of(GraphState.MESSAGES, List.of(AiMessage.from(
                        ask("Summarize the following command output:\n\n" + state.text("command_output")))))))
                .addEdge(START, "execute")
                .addEdge("execute", "summarize")
                .addEdge("summarize", END);
    }

    String runCommand(String command) throws IOException, InterruptedException {
        if (!allowedCommands.contains(command)) {
            throw new IllegalArgumentException("Command not allowed: '" + command + "'. Allowed: " + allowedCommands);
        }
        ProcessBuilder pb = WINDOWS ? new ProcessBuilder("cmd", "/c", command) : new ProcessBuilder("sh", "-c", command);
        Process process = pb.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "Error: command timed out";
        }
        return process.exitValue() == 0 ? output : "Error: " + output;
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("command", WINDOWS ? "dir" : "ls -l");
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("command", str(input, "command", WINDOWS ? "dir" : "ls -l")));
        return Map.of("output", state.text("command_output"), "summary", state.lastText());
    }
}
