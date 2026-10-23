package com.andrewsselvaraj.langgraph.core;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** All workflows, looked up by number ({@code "14"}, {@code "4"}) or full key ({@code "14-budget-data-summarization"}). */
@Component
public class WorkflowRegistry {

    private final List<Workflow> workflows;

    public WorkflowRegistry(List<Workflow> workflows) {
        this.workflows = workflows.stream().sorted(Comparator.comparing(Workflow::id)).toList();
    }

    public List<Workflow> all() {
        return workflows;
    }

    public Optional<Workflow> find(String idOrKey) {
        String wanted = idOrKey.trim().toLowerCase();
        String padded = wanted.matches("\\d") ? "0" + wanted : wanted;
        return workflows.stream()
                .filter(w -> w.id().equals(padded) || w.key().equals(wanted) || w.slug().equals(wanted))
                .findFirst();
    }
}
