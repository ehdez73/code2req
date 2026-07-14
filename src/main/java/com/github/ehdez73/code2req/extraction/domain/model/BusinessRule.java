package com.github.ehdez73.code2req.extraction.domain.model;

public record BusinessRule(
    String ruleId,
    String description,
    String precondition,
    String postcondition,
    String errorBehavior,
    String sourceFile,
    int startLine,
    int endLine,
    ExternalCall externalCall
) {
    public BusinessRule(String ruleId, String description, String precondition,
                        String postcondition, String errorBehavior,
                        String sourceFile, int startLine, int endLine) {
        this(ruleId, description, precondition, postcondition, errorBehavior,
             sourceFile, startLine, endLine, null);
    }
}
