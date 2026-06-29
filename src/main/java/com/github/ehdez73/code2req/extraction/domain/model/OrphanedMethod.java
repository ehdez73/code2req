package com.github.ehdez73.code2req.extraction.domain.model;

public record OrphanedMethod(
    String className,
    String methodName,
    String filePath,
    int startLine,
    int endLine,
    String reason
) {}
