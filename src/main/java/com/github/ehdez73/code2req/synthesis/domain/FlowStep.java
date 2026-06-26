package com.github.ehdez73.code2req.synthesis.domain;

import java.util.List;

public record FlowStep(
    int stepIndex,
    FlowStepComponentType componentType,
    String className,
    String methodName,
    String businessPurpose,
    String sourceFile,
    int startLine,
    int endLine,
    List<String> enrichments
) {}
