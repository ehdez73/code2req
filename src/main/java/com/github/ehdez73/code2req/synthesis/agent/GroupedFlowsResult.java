package com.github.ehdez73.code2req.synthesis.agent;

import com.github.ehdez73.code2req.synthesis.domain.FunctionalFeature;

import java.util.List;

public record GroupedFlowsResult(
    List<FunctionalFeature> features
) {}
