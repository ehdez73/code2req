package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestFeature(
    String featureId,
    String name,
    String description,
    List<ManifestFlow> flows
) {
    public ManifestFeature {
        flows = flows != null ? flows : List.of();
    }
}
