package com.andrewsselvaraj.langgraph.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * {@code --workflow=<id>} runs one workflow in the terminal, like {@code python main.py}.
 * Any other {@code --key=value} option becomes an input value (e.g. {@code --question="..."}).
 * {@code --workflow=list} prints the catalogue.
 */
@Component
public class WorkflowCommandLineRunner implements ApplicationRunner {

    private final WorkflowRegistry registry;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public WorkflowCommandLineRunner(WorkflowRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> ids = args.getOptionValues("workflow");
        if (ids == null || ids.isEmpty()) {
            return;
        }
        PrintStream out = System.out;
        String id = ids.getFirst();
        if ("list".equalsIgnoreCase(id)) {
            registry.all().forEach(w -> out.printf("%-46s %s%n", w.key(), w.title()));
            return;
        }
        Workflow workflow = registry.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown workflow '" + id + "'. Try --workflow=list"));

        out.println("=== " + workflow.key() + ": " + workflow.title() + " ===");
        Map<String, Object> input = new HashMap<>(workflow.sampleInput());
        boolean batch = args.containsOption("batch");
        for (String name : args.getOptionNames()) {
            if (!name.equals("workflow") && !name.equals("batch")
                    && !name.startsWith("spring.") && !name.startsWith("app.")) {
                input.put(name, args.getOptionValues(name).getFirst());
                batch = true;
            }
        }

        if (workflow instanceof ConsoleWorkflow console && !batch) {
            console.runConsole(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), out);
        } else {
            Map<String, Object> result = workflow.run(input);
            out.println(json.writeValueAsString(result));
        }
    }
}
