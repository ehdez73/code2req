package com.github.ehdez73.code2req.synthesis.domain;

public record EntryPoint(
    EntryPointType type,
    String className,
    String methodName,
    String filePath,
    String identifier,
    double priorityScore
) {}
