package com.github.ehdez73.code2req.extraction.adapter.agent.model;

import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;

import java.util.List;

public record EnrichedFlowResult(
    List<ExecutionFlow> flows
) {}
