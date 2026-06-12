package com.github.ehdez73.code2req.model;

public record Dependency(
    String groupId,
    String artifactId,
    String version,
    String scope
) {}
