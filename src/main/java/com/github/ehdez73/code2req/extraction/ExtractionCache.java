package com.github.ehdez73.code2req.extraction;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationship;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ExtractionCache(
    CrossReferencedResult crossRefResult,
    List<OrphanedMethod> orphanedMethods,
    List<AmbiguityGap> quarantineGaps,
    Set<String> flowsWithStaleLinks
) {
    public ExtractionCache(CrossReferencedResult crossRefResult,
                           List<OrphanedMethod> orphanedMethods,
                           List<AmbiguityGap> quarantineGaps) {
        this(crossRefResult, orphanedMethods, quarantineGaps, new HashSet<>());
    }

    public static ExtractionCache mergeFlow(ExtractionCache existing, FunctionalFlow newFlow,
                                             List<AmbiguityGap> newGaps, boolean isRegroup) {
        String flowId = newFlow.flowId();
        List<FunctionalFeature> newFeatures = new ArrayList<>();
        boolean found = false;

        if (existing != null) {
            for (FunctionalFeature feature : existing.crossRefResult().features()) {
                List<FunctionalFlow> updatedFlows = new ArrayList<>();
                for (FunctionalFlow f : feature.flows()) {
                    if (f.flowId().equals(flowId)) {
                        updatedFlows.add(newFlow);
                        found = true;
                    } else {
                        updatedFlows.add(f);
                    }
                }
                if (!updatedFlows.isEmpty()) {
                    newFeatures.add(new FunctionalFeature(
                        feature.featureId(), feature.name(), feature.description(),
                        updatedFlows, feature.relationships()));
                }
            }
        }

        if (!found) {
            newFeatures.add(new FunctionalFeature(
                "feature-" + (newFeatures.size() + 1), newFlow.name(),
                "Auto-generated feature for " + newFlow.name(),
                List.of(newFlow), List.of()));
        }

        List<FlowRelationship> updatedRelationships = existing != null
            ? existing.crossRefResult().crossFlowRelationships().stream()
                .filter(r -> !r.sourceFlowId().equals(flowId) && !r.targetFlowId().equals(flowId))
                .toList()
            : List.of();

        List<OrphanedMethod> orphans = existing != null ? existing.orphanedMethods() : List.of();

        List<AmbiguityGap> updatedGaps;
        if (existing != null) {
            updatedGaps = new ArrayList<>(existing.quarantineGaps().stream()
                .filter(g -> !(g.flowId() != null && g.flowId().equals(flowId)))
                .toList());
        } else {
            updatedGaps = new ArrayList<>();
        }
        updatedGaps.addAll(newGaps);

        Set<String> staleLinks;
        if (isRegroup) {
            staleLinks = new HashSet<>();
        } else {
            staleLinks = existing != null
                ? new HashSet<>(existing.flowsWithStaleLinks())
                : new HashSet<>();
            staleLinks.add(flowId);
        }

        CrossReferencedResult updatedCrossRef = new CrossReferencedResult(newFeatures, updatedRelationships);
        return new ExtractionCache(updatedCrossRef, orphans, updatedGaps, staleLinks);
    }

    public static ExtractionCache createWithSingleFlow(FunctionalFlow flow, List<AmbiguityGap> gaps) {
        return mergeFlow(null, flow, gaps, false);
    }

    public ExtractionCache withClearedStaleLinks() {
        return new ExtractionCache(crossRefResult, orphanedMethods, quarantineGaps, new HashSet<>());
    }
}
