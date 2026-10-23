package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P24CodingAgentMini;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 24: generates one HTML page, reviews it and saves it to output/index.html. */
@RestController
public class CodingAgentMiniController {

    public record Request(@NotBlank String request) {
    }

    public record Response(String savedTo, String review) {
    }

    private final P24CodingAgentMini workflow;

    public CodingAgentMiniController(P24CodingAgentMini workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/coding-agent-mini/pages")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("request", request.request())), Response.class);
    }
}
