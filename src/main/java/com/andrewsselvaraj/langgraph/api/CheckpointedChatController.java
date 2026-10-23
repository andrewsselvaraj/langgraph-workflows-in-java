package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P07DocumentSummarizationPipeline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Project 07: chat with a checkpointer. Reuse threadId to continue; repeat sends the message several times. */
@RestController
public class CheckpointedChatController {

    public record Request(@NotBlank String message, String threadId, @Min(1) @Max(5) Integer repeat) {
    }

    public record Response(String answer, String threadId, int messagesInThread) {
    }

    private final P07DocumentSummarizationPipeline workflow;

    public CheckpointedChatController(P07DocumentSummarizationPipeline workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/checkpointed-chat")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("message", request.message(), "threadId", request.threadId(), "repeat", request.repeat())), Response.class);
    }
}
