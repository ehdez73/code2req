package com.github.ehdez73.code2req.indexing.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "code2req.indexing")
public record IndexingConfig(
    Integer maxDiscoveryDepth,
    List<String> testSuffixes
) {
    public int resolvedMaxDiscoveryDepth() {
        return maxDiscoveryDepth != null ? maxDiscoveryDepth : 3;
    }

    public List<String> resolvedTestSuffixes() {
        return testSuffixes != null && !testSuffixes.isEmpty() ? testSuffixes : List.of("Test", "IT");
    }
}
