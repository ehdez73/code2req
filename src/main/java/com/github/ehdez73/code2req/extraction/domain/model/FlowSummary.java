package com.github.ehdez73.code2req.extraction.domain.model;

public record FlowSummary(
    String shortId,
    String fullFlowId,
    EntryPointType type,
    String name,
    FlowSummaryStatus status,
    ComplexityLevel complexity,
    int stepCount,
    int unresolvedLinkCount,
    boolean hasStaleLinks
) {}