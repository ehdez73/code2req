package com.github.ehdez73.code2req.extraction.domain.model;

public record EdgeCase(
    String scenario,
    String businessConsequence,
    String sourceFile,
    int startLine,
    int endLine,
    String severity
) {
    public EdgeCase(String scenario, String businessConsequence,
                    String sourceFile, int startLine, int endLine) {
        this(scenario, businessConsequence, sourceFile, startLine, endLine, "MEDIUM");
    }
}
