package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P01BasicChatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 01: one LLM reply per message, no memory. */
@RestController
public class BasicChatbotController {

    public record Request(@NotBlank String message) {
    }

    public record Response(String answer) {
    }

    private final P01BasicChatbot workflow;

    public BasicChatbotController(P01BasicChatbot workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/basic-chatbot/chat")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("message", request.message())), Response.class);
    }
}
