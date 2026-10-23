package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P06IterativeProcessingWorkflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

/** Project 06: processes each item with the LLM, then aggregates the results (map-reduce). */
@RestController
public class ItemProcessingController {

    public record Request(@NotEmpty List<@NotBlank String> items) {
    }

    public record Response(String summary, List<String> results) {
    }

    private final P06IterativeProcessingWorkflow workflow;

    public ItemProcessingController(P06IterativeProcessingWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/items/process")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("items", request.items())), Response.class);
    }
}
