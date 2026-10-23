package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P17LogAnalysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 17: keeps the ERROR lines of a log and summarises them. */
@RestController
public class LogAnalysisController {

    public record Request(@NotBlank String logContent) {
    }

    public record Response(String errors, String summary) {
    }

    private final P17LogAnalysis workflow;

    public LogAnalysisController(P17LogAnalysis workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/log-analyses")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("log_content", request.logContent())), Response.class);
    }
}
