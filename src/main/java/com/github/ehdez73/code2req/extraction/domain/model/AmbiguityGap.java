package com.github.ehdez73.code2req.extraction.domain.model;

public record AmbiguityGap(
    String flowId,
    String flowName,
    String filePath,
    String missingContext,
    String suggestedApproach,
    double confidence,
    GapReason reason,
    boolean userProvided,
    String selectedOption,
    String userAnswer
) {
    public AmbiguityGap(String flowId, String flowName, String filePath,
                        String missingContext, String suggestedApproach,
                        double confidence, GapReason reason) {
        this(flowId, flowName, filePath, missingContext, suggestedApproach,
             confidence, reason, false, null, null);
    }
}
