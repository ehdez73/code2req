package com.github.ehdez73.code2req.extraction.domain.model;

public record BusinessRule(
    String ruleId,
    String description,
    String precondition,
    String postcondition,
    String errorBehavior,
    String sourceFile,
    int startLine,
    int endLine
) {}
