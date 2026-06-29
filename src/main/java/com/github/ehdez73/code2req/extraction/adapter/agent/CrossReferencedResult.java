package com.github.ehdez73.code2req.extraction.adapter.agent;

import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationship;

import java.util.List;

public record CrossReferencedResult(
    List<FunctionalFeature> features,
    List<FlowRelationship> crossFlowRelationships
) {}
