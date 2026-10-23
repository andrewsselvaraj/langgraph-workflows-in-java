package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;

/** project-15-resume-job-description-matching: {@code compare} (embedding similarity) {@code -> feedback}. */
@Component
public class P15ResumeJobDescriptionMatching extends AbstractWorkflow {

    static final String RESUME = "Experienced software engineer with Python and machine learning skills.";
    static final String JOB = "Seeking a Python developer with experience in AI and data analysis.";

    private final EmbeddingModel embeddings;

    public P15ResumeJobDescriptionMatching(ChatModel llm, @Lazy EmbeddingModel embeddings) {
        super(llm, "15", "resume-job-description-matching", "Resume / job match",
                "Scores a resume against a job description with embeddings, then writes feedback.");
        this.embeddings = embeddings;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("compare", node_async(state -> {
                    float[] resume = embeddings.embed(state.text("resume_text"));
                    float[] job = embeddings.embed(state.text("job_description"));
                    return Map.of("match_score", String.format(java.util.Locale.ROOT, "%.4f", dot(resume, job)));
                }))
                .addNode("feedback", node_async(state -> Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(ask(
                        "Based on a match score of " + state.text("match_score")
                        + " (embedding similarity, 1.0 = identical), provide feedback on how well the resume matches "
                        + "the job description.\n\nResume: " + state.text("resume_text")
                        + "\n\nJob description: " + state.text("job_description")))))))
                .addEdge(START, "compare")
                .addEdge("compare", "feedback")
                .addEdge("feedback", END);
    }

    /** Simple dot product, as in the Python version (MiniLM vectors are normalised, so this is cosine similarity). */
    static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("resume_text", RESUME, "job_description", JOB);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(
                "resume_text", str(input, "resume_text", RESUME),
                "job_description", str(input, "job_description", JOB)));
        return Map.of("matchScore", state.text("match_score"), "feedback", state.lastText());
    }
}
