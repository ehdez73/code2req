package com.github.ehdez73.code2req.synthesis.agent;

import com.github.ehdez73.code2req.synthesis.domain.ExecutionFlow;

import java.util.List;

public record TracedFlowResult(
    List<ExecutionFlow> flows,
    List<String> allQuarantinedFlowIds
) {}
