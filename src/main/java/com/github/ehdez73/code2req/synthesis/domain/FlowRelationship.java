package com.github.ehdez73.code2req.synthesis.domain;

public record FlowRelationship(
    String sourceFlowId,
    String targetFlowId,
    FlowRelationshipType type,
    String description
) {}
