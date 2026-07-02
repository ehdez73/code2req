package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestBusinessRule(
    String ruleId,
    String description,
    String precondition,
    String postcondition,
    String errorBehavior,
    String sourceFile,
    Integer startLine,
    Integer endLine,
    ManifestExternalCall externalCall
) {}
