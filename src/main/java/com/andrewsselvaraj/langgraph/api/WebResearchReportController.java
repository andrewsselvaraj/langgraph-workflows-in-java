package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P18WebResearchReport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 18: researches a topic on the web and writes a report. Needs TAVILY_API_KEY. */
@RestController
public class WebResearchReportController {

    public record Request(@NotBlank String topic) {
    }

    public record Response(String report, String sources) {
    }

    private final P18WebResearchReport workflow;

    public WebResearchReportController(P18WebResearchReport workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/web-reports")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("topic", request.topic())), Response.class);
    }
}
