package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P03HumanInTheLoopChatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Project 03: ask a question, get a draft that waits for approval, then approve or reject it.
 *
 * <pre>
 * POST /api/hitl/questions                     {"question": "..."}  -> 201 {threadId, draft}
 * POST /api/hitl/questions/{threadId}/approval {"answer": "yes"}    -> 200 {approved, answer}
 * </pre>
 */
@RestController
@RequestMapping("/api/hitl/questions")
public class HumanInTheLoopController {

    public record QuestionRequest(@NotBlank String question) {
    }

    public record DraftResponse(String status, String threadId, String draft) {
    }

    public record ApprovalRequest(@NotBlank String answer) {
    }

    public record FinalResponse(String status, String threadId, boolean approved, String answer) {
    }

    private final P03HumanInTheLoopChatbot workflow;

    public HumanInTheLoopController(P03HumanInTheLoopChatbot workflow) {
        this.workflow = workflow;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftResponse ask(@Valid @RequestBody QuestionRequest request) throws Exception {
        return as(workflow.start(request.question()), DraftResponse.class);
    }

    @PostMapping("/{threadId}/approval")
    public FinalResponse approve(@PathVariable String threadId, @Valid @RequestBody ApprovalRequest request) throws Exception {
        return as(workflow.resume(threadId, request.answer()), FinalResponse.class);
    }
}
