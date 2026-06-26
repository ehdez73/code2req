package com.github.ehdez73.code2req.synthesis.domain;

public record EdgeCase(
    String scenario,
    String businessConsequence,
    String sourceFile,
    int startLine,
    int endLine
) {}
