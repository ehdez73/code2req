package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestStep(
    int stepIndex,
    String componentType,
    String className,
    String methodName,
    String businessPurpose,
    String sourceFile,
    int startLine,
    int endLine
) {}
