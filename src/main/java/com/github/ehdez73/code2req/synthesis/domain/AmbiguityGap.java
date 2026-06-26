package com.github.ehdez73.code2req.synthesis.domain;

public record AmbiguityGap(
    String flowId,
    String missingContext,
    String suggestedApproach,
    double confidence,
    GapReason reason
) {}
