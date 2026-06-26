package com.github.ehdez73.code2req.synthesis.domain;

import java.util.List;

public record FunctionalFeature(
    String featureId,
    String name,
    String description,
    List<FunctionalFlow> flows,
    List<FlowRelationship> relationships
) {}
