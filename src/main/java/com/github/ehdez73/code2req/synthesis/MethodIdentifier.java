package com.github.ehdez73.code2req.synthesis;

public record MethodIdentifier(
    String className,
    String methodName,
    String filePath
) {}
