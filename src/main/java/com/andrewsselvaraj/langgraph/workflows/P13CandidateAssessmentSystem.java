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
import com.andrewsselvaraj.langgraph.core.TextRetriever;

/** project-13-candidate-assessment-system (interview coach): retrieve criteria, assess the answer. */
@Component
public class P13CandidateAssessmentSystem extends AbstractWorkflow {

    static final String CRITERIA = "Key skills: Python, LangGraph, problem-solving. Look for clear explanations.";

    private final EmbeddingModel embeddings;

    public P13CandidateAssessmentSystem(ChatModel llm, @Lazy EmbeddingModel embeddings) {
        super(llm, "13", "candidate-assessment-system", "Interview coach",
                "Retrieves the assessment criteria from a vector store and grades a candidate's answer.");
        this.embeddings = embeddings;
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("assessor", node_async(state -> {
                    String context = TextRetriever.of(embeddings, state.text("criteria")).retrieve("assessment criteria");
                    String verdict = ask("Assess the candidate response based on these criteria: " + context
                            + "\nCandidate response: " + state.text("candidate_response"));
                    return Map.of(GraphState.MESSAGES, List.of(new AssistantMessage(verdict)));
                }))
                .addEdge(START, "assessor")
                .addEdge("assessor", END);
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("candidate_response", "I solved the problem using a state machine in Python.", "criteria", CRITERIA);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of(
                "candidate_response", str(input, "candidate_response", ""),
                "criteria", str(input, "criteria", CRITERIA)));
        return Map.of("assessment", state.lastText());
    }
}
