package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P19ShellCommandSummarization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 19: runs an allow-listed command (app.shell.allowed-commands) and summarises its output. */
@RestController
public class ShellCommandController {

    public record Request(@NotBlank String command) {
    }

    public record Response(String output, String summary) {
    }

    private final P19ShellCommandSummarization workflow;

    public ShellCommandController(P19ShellCommandSummarization workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/command-summaries")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("command", request.command())), Response.class);
    }
}
