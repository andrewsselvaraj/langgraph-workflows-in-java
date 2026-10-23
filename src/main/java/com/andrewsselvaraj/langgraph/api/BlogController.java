package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.andrewsselvaraj.langgraph.workflows.P22BlogWritingAndPublishingAgent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

/** Project 22: researches URLs, writes and proofreads a blog post, then publishes it to WordPress if configured. */
@RestController
public class BlogController {

    public record Request(@NotEmpty List<@NotBlank String> urls, String organizationName, String organizationDescription) {
    }

    public record Response(boolean published, String title, String postId, String blogPost) {
    }

    private final P22BlogWritingAndPublishingAgent workflow;

    public BlogController(P22BlogWritingAndPublishingAgent workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/blog-posts/publish")
    public Response run(@Valid @RequestBody Request request) throws Exception {
        return as(workflow.run(input("urls", request.urls(), "organization_name", request.organizationName(), "organization_description", request.organizationDescription())), Response.class);
    }
}
