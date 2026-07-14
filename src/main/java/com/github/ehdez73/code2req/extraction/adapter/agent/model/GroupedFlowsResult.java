package com.github.ehdez73.code2req.extraction.adapter.agent.model;

import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;

import java.util.List;

public record GroupedFlowsResult(
    List<FunctionalFeature> features
) {}
