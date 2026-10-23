package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P11ResearchAndReportGeneration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 11: a researcher agent then a writer agent turn a topic into a blog post. */
@RestController
public class ResearchReportController {

    public record Request(@NotBlank String topic) {
    }

    public record Response(String research, String blogPost) {
    }

    private final P11ResearchAndReportGeneration workflow;

    public ResearchReportController(P11ResearchAndReportGeneration workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/research-reports")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("topic", request.topic())), Response.class);
    }
}
