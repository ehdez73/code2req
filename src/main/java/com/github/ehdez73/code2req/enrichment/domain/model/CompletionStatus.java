package com.github.ehdez73.code2req.enrichment.domain.model;

import java.util.List;

public record CompletionStatus(
    int tasksSubmitted,
    int tasksCompleted,
    int tasksFailed,
    int dependenciesDiscovered,
    int tokensConsumed,
    double apiCostEstimated,
    List<String> awaitingHumanReview
) {
    public boolean allSucceeded() {
        return tasksFailed == 0 && awaitingHumanReview.isEmpty();
    }
}
