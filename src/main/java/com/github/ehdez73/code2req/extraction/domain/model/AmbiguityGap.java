package com.github.ehdez73.code2req.extraction.domain.model;

public record AmbiguityGap(
    String flowId,
    String missingContext,
    String suggestedApproach,
    double confidence,
    GapReason reason
) {}
