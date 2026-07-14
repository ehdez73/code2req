package com.github.ehdez73.code2req.extraction.domain.model;

public record FlowRelationship(
    String sourceFlowId,
    String targetFlowId,
    FlowRelationshipType type,
    String description
) {}
