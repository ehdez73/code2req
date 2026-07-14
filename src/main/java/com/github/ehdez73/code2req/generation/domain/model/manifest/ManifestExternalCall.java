package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestExternalCall(
    String httpMethod,
    String url,
    Integer timeoutMs,
    String retryStrategy,
    String fallbackBehavior
) {}
