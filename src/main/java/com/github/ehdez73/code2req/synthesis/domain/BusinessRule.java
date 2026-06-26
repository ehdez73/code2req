package com.github.ehdez73.code2req.synthesis.domain;

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
