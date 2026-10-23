package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P02AgentWithSearchTool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 02: ReAct agent with web search. Send the returned threadId back to continue the conversation. */
@RestController
public class SearchAgentController {

    public record Request(@NotBlank String message, String threadId) {
    }

    public record Response(String answer, String threadId) {
    }

    private final P02AgentWithSearchTool workflow;

    public SearchAgentController(P02AgentWithSearchTool workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/search-agent/chat")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("message", request.message(), "threadId", request.threadId())), Response.class);
    }
}
