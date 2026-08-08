package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.extraction.ExtractionCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowSummaryAssembler {

    private final StructuralGraph structuralGraph;
    private final ExtractionCache extractionCache;

    public FlowSummaryAssembler(StructuralGraph structuralGraph, ExtractionCache extractionCache) {
        this.structuralGraph = structuralGraph;
        this.extractionCache = extractionCache;
    }

    public List<FlowSummary> assemble() {
        List<FlowSummary> summaries = new ArrayList<>();
        Set<String> cacheFlowIds = new HashSet<>();

        if (extractionCache != null && extractionCache.crossRefResult() != null
                && extractionCache.crossRefResult().features() != null) {
            Set<String> staleLinksSet = extractionCache.flowsWithStaleLinks() != null
                ? extractionCache.flowsWithStaleLinks() : Set.of();

            for (var feature : extractionCache.crossRefResult().features()) {
                if (feature.flows() == null) continue;
                for (FunctionalFlow flow : feature.flows()) {
                    cacheFlowIds.add(flow.flowId());
                    summaries.add(cacheFlowSummary(flow, staleLinksSet));
                }
            }
        }

        List<EntryPoint> entryPoints = structuralGraph.getEntryPoints();
        for (EntryPoint ep : entryPoints) {
            if (!cacheFlowIds.contains(ep.id())) {
                summaries.add(pendingFlowSummary(ep));
            }
        }

        return summaries;
    }

    private FlowSummary pendingFlowSummary(EntryPoint ep) {
        return new FlowSummary(
            ep.shortId(),
            ep.id(),
            ep.type(),
            entryPointName(ep),
            FlowSummaryStatus.PENDING,
            null,
            0,
            0,
            false
        );
    }

    private FlowSummary cacheFlowSummary(FunctionalFlow flow, Set<String> staleLinksSet) {
        return new FlowSummary(
            flow.entryPoint() != null ? flow.entryPoint().shortId() : "",
            flow.flowId(),
            flow.entryPoint() != null ? flow.entryPoint().type() : null,
            flow.name() != null ? flow.name() : entryPointName(flow.entryPoint()),
            flow.complexity() != null ? FlowSummaryStatus.ANALYZED : FlowSummaryStatus.QUARANTINED,
            flow.complexity(),
            flow.steps() != null ? flow.steps().size() : 0,
            countUnresolvedLinks(flow),
            staleLinksSet.contains(flow.flowId())
        );
    }

    private int countUnresolvedLinks(FunctionalFlow flow) {
        return 0;
    }

    private static String entryPointName(EntryPoint ep) {
        if (ep == null) return "Unknown";
        return switch (ep) {
            case HttpEntryPoint h -> h.httpMethod() + " " + h.path();
            case ScheduledEntryPoint s -> s.schedule();
            case KafkaEntryPoint k -> String.join(", ", k.topics());
            case RabbitMqEntryPoint r -> String.join(", ", r.queues());
            case ActiveMqEntryPoint a -> a.destination();
            case EventListenerEntryPoint e -> e.payloadType() != null ? e.payloadType() : "EventListener";
        };
    }
}
