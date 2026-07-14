package com.github.ehdez73.code2req.extraction.domain.model;

public record MethodIdentifier(
    String className,
    String methodName,
    String filePath
) {}
