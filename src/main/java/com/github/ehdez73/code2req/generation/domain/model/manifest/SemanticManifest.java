package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SemanticManifest(
    String manifestVersion,
    String systemName,
    String generatedAt,
    List<ManifestFeature> features,
    List<ManifestCrossFlowRelationship> crossFlowRelationships,
    List<ManifestOrphanedMethod> orphanedMethods
) {
    public SemanticManifest {
        features = features != null ? features : List.of();
        crossFlowRelationships = crossFlowRelationships != null ? crossFlowRelationships : List.of();
        orphanedMethods = orphanedMethods != null ? orphanedMethods : List.of();
    }
}
