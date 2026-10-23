package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P20MultiDomainRoutingAgent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 20: routes the message to the finance, HR, marketing or general agent. */
@RestController
public class AssistantController {

    public record Request(@NotBlank String message) {
    }

    public record Response(String routedTo, String answer) {
    }

    private final P20MultiDomainRoutingAgent workflow;

    public AssistantController(P20MultiDomainRoutingAgent workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/assistant")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("message", request.message())), Response.class);
    }
}
