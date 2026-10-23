package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P13CandidateAssessmentSystem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 13: grades an interview answer against criteria retrieved from a vector store. */
@RestController
public class CandidateAssessmentController {

    public record Request(@NotBlank String candidateResponse, String criteria) {
    }

    public record Response(String assessment) {
    }

    private final P13CandidateAssessmentSystem workflow;

    public CandidateAssessmentController(P13CandidateAssessmentSystem workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/candidate-assessments")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("candidate_response", request.candidateResponse(), "criteria", request.criteria())), Response.class);
    }
}
