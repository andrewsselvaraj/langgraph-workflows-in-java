package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P09CreativeContentGeneration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Project 09: generates a marketing slogan and refines it for the given number of rounds. */
@RestController
public class SloganController {

    public record Request(@NotBlank String product, @Min(1) @Max(5) Integer rounds) {
    }

    public record Response(String slogan, List<String> history) {
    }

    private final P09CreativeContentGeneration workflow;

    public SloganController(P09CreativeContentGeneration workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/slogans")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("product", request.product(), "rounds", request.rounds())), Response.class);
    }
}
