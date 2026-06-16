package com.github.ehdez73.code2req.model;

import java.time.LocalDateTime;

public record Metric(
    String runId,
    int phase,
    int tasksTotal,
    int tasksCompleted,
    int edgesResolved,
    int edgesUnresolved,
    int topicLinksResolved,
    int floatingLinksRegistered,
    int tokensConsumed,
    double apiCostEstimated,
    String recordedAt
) {
    public Metric(String runId, int phase) {
        this(runId, phase, 0, 0, 0, 0, 0, 0, 0, 0.0, LocalDateTime.now().toString());
    }
}