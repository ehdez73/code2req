package com.github.ehdez73.code2req.common.domain;

public record Dependency(
    String groupId,
    String artifactId,
    String version,
    String scope
) {}
