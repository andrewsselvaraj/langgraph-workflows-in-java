package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P12EmailResponseAutomation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 12: parses an email and drafts a reply. */
@RestController
public class EmailReplyController {

    public record Request(@NotBlank String email) {
    }

    public record Response(String reply) {
    }

    private final P12EmailResponseAutomation workflow;

    public EmailReplyController(P12EmailResponseAutomation workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/email-replies")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("email", request.email())), Response.class);
    }
}
