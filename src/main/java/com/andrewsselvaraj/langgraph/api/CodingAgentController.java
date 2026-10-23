package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P23CodingAgent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 23: PRD, tasks, code and review, then saves (and optionally deploys) the generated site. */
@RestController
public class CodingAgentController {

    public record Request(@NotBlank String requirement) {
    }

    public record Response(String deployedUrl, String review) {
    }

    private final P23CodingAgent workflow;

    public CodingAgentController(P23CodingAgent workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/coding-agent/builds")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("requirement", request.requirement())), Response.class);
    }
}
