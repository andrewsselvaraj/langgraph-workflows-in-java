package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P05MultiAgentCollaboration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 05: the chatbot loops on itself until the conversation passes five messages. */
@RestController
public class ConversationLoopController {

    public record Request(@NotBlank String message) {
    }

    public record Response(String answer, int messageCount) {
    }

    private final P05MultiAgentCollaboration workflow;

    public ConversationLoopController(P05MultiAgentCollaboration workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/conversation-loop")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("message", request.message())), Response.class);
    }
}
