package com.github.ehdez73.code2req.indexing.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the indexing phase (Phase 1) discovery depth and test filtering.
 *
 * @param maxDiscoveryDepth Maximum depth for code graph discovery during indexing
 * @param testSuffixes      File suffixes to treat as test files and exclude from analysis
 * @param testPrefixes      File prefixes to treat as test files (e.g. "Test" for TestOrderService)
 */
@ConfigurationProperties(prefix = "code2req.indexing")
public record IndexingConfig(
    Integer maxDiscoveryDepth,
    List<String> testSuffixes,
    List<String> testPrefixes
) {
    public int resolvedMaxDiscoveryDepth() {
        return maxDiscoveryDepth != null ? maxDiscoveryDepth : 3;
    }

    public List<String> resolvedTestSuffixes() {
        return testSuffixes != null && !testSuffixes.isEmpty() ? testSuffixes : List.of("Test", "IT");
    }

    public List<String> resolvedTestPrefixes() {
        return testPrefixes != null ? testPrefixes : List.of();
    }
}
