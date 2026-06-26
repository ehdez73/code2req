package com.github.ehdez73.code2req.synthesis.agent;

import com.github.ehdez73.code2req.synthesis.domain.FunctionalFeature;
import com.github.ehdez73.code2req.synthesis.domain.FlowRelationship;

import java.util.List;

public record CrossReferencedResult(
    List<FunctionalFeature> features,
    List<FlowRelationship> crossFlowRelationships
) {}
