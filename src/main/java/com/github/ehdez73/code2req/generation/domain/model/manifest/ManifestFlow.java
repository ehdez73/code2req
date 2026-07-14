package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestFlow(
    String flowId,
    ManifestEntryPoint entryPoint,
    List<ManifestStep> steps,
    String userStory,
    List<ManifestAcceptanceCriterion> acceptanceCriteria,
    List<ManifestBusinessRule> businessRules,
    List<ManifestEdgeCase> edgeCases,
    List<ManifestNonFunctionalRequirement> nonFunctionalRequirements,
    @JsonInclude(Include.NON_NULL) String mermaidDiagram,
    String complexity,
    boolean reviewRequired,
    ManifestUnresolvedReason unresolvedReason,
    ManifestTraceabilityGraph traceabilityGraph
) {
    public ManifestFlow {
        steps = steps != null ? steps : List.of();
        acceptanceCriteria = acceptanceCriteria != null ? acceptanceCriteria : List.of();
        businessRules = businessRules != null ? businessRules : List.of();
        edgeCases = edgeCases != null ? edgeCases : List.of();
        nonFunctionalRequirements = nonFunctionalRequirements != null ? nonFunctionalRequirements : List.of();
    }
}
