package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestTraceabilityGraph(
    List<ManifestTraceabilityNode> nodes,
    List<ManifestTraceabilityEdge> edges
) {
    public ManifestTraceabilityGraph {
        nodes = nodes != null ? nodes : List.of();
        edges = edges != null ? edges : List.of();
    }
}
