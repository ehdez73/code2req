package com.github.ehdez73.code2req.extraction.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for quarantine thresholds in Phase 3 GOAP agent flow analysis.
 *
 * @param ambiguityConfidenceThreshold Confidence threshold below which ambiguity triggers quarantine
 * @param maxUnresolvedCalls           Maximum unresolved calls allowed before quarantine
 */
@ConfigurationProperties(prefix = "code2req.quarantine")
public record QuarantineConfig(
    Double ambiguityConfidenceThreshold,
    Integer maxUnresolvedCalls
) {
    public static final double DEFAULT_AMBIGUITY_CONFIDENCE_THRESHOLD = 0.3;
    public static final int DEFAULT_MAX_UNRESOLVED_CALLS = 3;

    public double resolvedAmbiguityConfidenceThreshold() {
        return ambiguityConfidenceThreshold != null ? ambiguityConfidenceThreshold : DEFAULT_AMBIGUITY_CONFIDENCE_THRESHOLD;
    }

    public int resolvedMaxUnresolvedCalls() {
        return maxUnresolvedCalls != null ? maxUnresolvedCalls : DEFAULT_MAX_UNRESOLVED_CALLS;
    }
}
