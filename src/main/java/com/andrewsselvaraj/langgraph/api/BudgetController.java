package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P14BudgetDataSummarization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 14: totals a Category,Amount CSV per category and summarises the budget. */
@RestController
public class BudgetController {

    public record Request(@NotBlank String csvData) {
    }

    public record Response(String totals, String summary) {
    }

    private final P14BudgetDataSummarization workflow;

    public BudgetController(P14BudgetDataSummarization workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/budget-summaries")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("csv_data", request.csvData())), Response.class);
    }
}
