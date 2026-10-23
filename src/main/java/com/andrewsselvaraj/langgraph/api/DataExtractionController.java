package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P08DataExtractionAgent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 08: searches the web for live data (for example a stock price) and extracts the answer. */
@RestController
public class DataExtractionController {

    public record Request(@NotBlank String question) {
    }

    public record Response(String answer, boolean usedWebSearch) {
    }

    private final P08DataExtractionAgent workflow;

    public DataExtractionController(P08DataExtractionAgent workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/data-extraction")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("message", request.question())), Response.class);
    }
}
