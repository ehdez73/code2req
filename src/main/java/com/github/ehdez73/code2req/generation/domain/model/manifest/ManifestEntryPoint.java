package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestEntryPoint(
    String type,
    String httpMethod,
    String path,
    String className,
    String methodName,
    String filePath,
    String schedule,
    String topicOrQueue
) {}
