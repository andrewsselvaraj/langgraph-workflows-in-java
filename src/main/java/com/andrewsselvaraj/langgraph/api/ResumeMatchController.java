package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P15ResumeJobDescriptionMatching;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 15: embedding similarity between a resume and a job description, plus feedback. */
@RestController
public class ResumeMatchController {

    public record Request(@NotBlank String resumeText, @NotBlank String jobDescription) {
    }

    public record Response(double matchScore, String feedback) {
    }

    private final P15ResumeJobDescriptionMatching workflow;

    public ResumeMatchController(P15ResumeJobDescriptionMatching workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/resume-matches")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("resume_text", request.resumeText(), "job_description", request.jobDescription())), Response.class);
    }
}
