package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestAcceptanceCriterion(
    String scenarioId,
    String name,
    List<String> given,
    List<String> when,
    List<String> then
) {
    public ManifestAcceptanceCriterion {
        given = given != null ? given : List.of();
        when = when != null ? when : List.of();
        then = then != null ? then : List.of();
    }
}
