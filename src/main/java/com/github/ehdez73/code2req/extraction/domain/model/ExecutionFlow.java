package com.github.ehdez73.code2req.extraction.domain.model;

import java.util.List;

public record ExecutionFlow(
    String flowId,
    EntryPoint entryPoint,
    List<FlowStep> steps,
    int depth,
    List<String> unresolvedCalls,
    FlowStatus status
) {}
