package com.andrewsselvaraj.langgraph.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns workflow failures into RFC 9457 problem responses. Graph nodes run asynchronously, so the
 * real cause is usually wrapped a few levels deep; the root cause decides the status.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason());
    }

    /** Bad input (unknown command, missing PDF, ...) is a 400; anything else, e.g. the LLM call failing, a 502. */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, java.util.concurrent.CompletionException.class})
    public ProblemDetail workflowFailed(Exception e) {
        Throwable root = rootCause(e);
        if (root instanceof IllegalArgumentException || root instanceof IllegalStateException) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, root.getMessage());
        }
        log.warn("Workflow failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Workflow failed: " + root.getMessage());
    }

    static Throwable rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
