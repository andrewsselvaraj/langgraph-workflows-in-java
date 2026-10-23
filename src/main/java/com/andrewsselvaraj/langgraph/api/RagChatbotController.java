package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P16RagChatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Project 16: RAG over a knowledge base. Send your own knowledgeBase text or use the sample one. */
@RestController
public class RagChatbotController {

    public record Request(@NotBlank String question, String knowledgeBase) {
    }

    public record Response(String answer, String context) {
    }

    private final P16RagChatbot workflow;

    public RagChatbotController(P16RagChatbot workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/rag/questions")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("question", request.question(), "knowledge_base", request.knowledgeBase())), Response.class);
    }
}
