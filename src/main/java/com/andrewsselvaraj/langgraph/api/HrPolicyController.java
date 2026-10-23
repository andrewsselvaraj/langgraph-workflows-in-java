package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P10DecisionMakingAgent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 10: HR policy Q&A over a vector store. Send your own policy text or use the sample one. */
@RestController
public class HrPolicyController {

    public record Request(@NotBlank String question, String policy) {
    }

    public record Response(String answer, String context) {
    }

    private final P10DecisionMakingAgent workflow;

    public HrPolicyController(P10DecisionMakingAgent workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/hr-policy/questions")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("question", request.question(), "policy", request.policy())), Response.class);
    }
}
