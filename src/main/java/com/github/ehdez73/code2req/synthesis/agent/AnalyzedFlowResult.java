package com.github.ehdez73.code2req.synthesis.agent;

import com.github.ehdez73.code2req.synthesis.domain.FunctionalFlow;

import java.util.List;

public record AnalyzedFlowResult(
    List<FunctionalFlow> flows
) {}
