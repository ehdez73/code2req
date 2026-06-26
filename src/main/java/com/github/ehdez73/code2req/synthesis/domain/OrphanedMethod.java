package com.github.ehdez73.code2req.synthesis.domain;

public record OrphanedMethod(
    String className,
    String methodName,
    String filePath,
    int startLine,
    int endLine,
    String reason
) {}
