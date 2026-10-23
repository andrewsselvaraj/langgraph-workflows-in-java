package com.andrewsselvaraj.langgraph.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST access to every workflow.
 *
 * <pre>
 * GET  /api/workflows              list
 * GET  /api/workflows/{id}         details + sample input
 * GET  /api/workflows/{id}/graph   Mermaid diagram
 * POST /api/workflows/{id}         run (JSON body = input, empty body = sample input)
 * </pre>
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowRegistry registry;

    public WorkflowController(WorkflowRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return registry.all().stream().map(WorkflowController::describe).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return describe(lookup(id));
    }

    @GetMapping(value = "/{id}/graph", produces = MediaType.TEXT_PLAIN_VALUE)
    public String graph(@PathVariable String id) throws Exception {
        return lookup(id).mermaid();
    }

    @PostMapping("/{id}")
    public Map<String, Object> run(@PathVariable String id,
                                   @RequestBody(required = false) Map<String, Object> input) throws Exception {
        Workflow workflow = lookup(id);
        Map<String, Object> merged = new HashMap<>(workflow.sampleInput());
        if (input != null) {
            merged.putAll(input);
        }
        return workflow.run(merged);
    }

    private Workflow lookup(String id) {
        return registry.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown workflow: " + id));
    }

    private static Map<String, Object> describe(Workflow w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.id());
        m.put("key", w.key());
        m.put("title", w.title());
        m.put("description", w.description());
        m.put("sampleInput", w.sampleInput());
        return m;
    }
}
