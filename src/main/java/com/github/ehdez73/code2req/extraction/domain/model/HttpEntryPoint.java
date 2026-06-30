package com.github.ehdez73.code2req.extraction.domain.model;

import java.util.List;

public record HttpEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String httpMethod,
    String path,
    List<String> pathVariables,
    List<String> requestBodies
) implements EntryPoint {
    @Override
    public EntryPointType type() { return EntryPointType.HTTP; }
}
