package com.github.ehdez73.code2req.extraction.adapter.agent;

import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;

import java.util.List;

public record TracedFlowResult(
    List<ExecutionFlow> flows,
    List<String> allQuarantinedFlowIds
) {}
