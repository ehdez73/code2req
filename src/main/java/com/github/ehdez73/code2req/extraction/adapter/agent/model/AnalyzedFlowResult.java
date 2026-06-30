package com.github.ehdez73.code2req.extraction.adapter.agent.model;

import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;

import java.util.List;

public record AnalyzedFlowResult(
    List<FunctionalFlow> flows
) {}
