package com.github.ehdez73.code2req.synthesis.domain;

import java.util.List;

public record EntryPoint(
    String id,
    EntryPointType type,
    String httpMethod,
    String path,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    List<String> pathVariables,
    String schedule,
    String topicOrQueue
) {}
