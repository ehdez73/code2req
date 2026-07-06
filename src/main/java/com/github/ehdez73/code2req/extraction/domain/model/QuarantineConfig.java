package com.github.ehdez73.code2req.extraction.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "code2req.quarantine")
public record QuarantineConfig(
    Integer maxSteps,
    Double ambiguityConfidenceThreshold,
    Integer maxHopDepth,
    Integer maxUnresolvedCalls,
    List<String> frameworkPrefixes
) {
    public static final int DEFAULT_MAX_STEPS = 20;
    public static final double DEFAULT_AMBIGUITY_CONFIDENCE_THRESHOLD = 0.3;
    public static final int DEFAULT_MAX_HOP_DEPTH = 5;
    public static final int DEFAULT_MAX_UNRESOLVED_CALLS = 3;
    public static final List<String> DEFAULT_FRAMEWORK_PREFIXES = List.of("org.springframework.", "com.fasterxml.jackson.");

    public int resolvedMaxSteps() {
        return maxSteps != null ? maxSteps : DEFAULT_MAX_STEPS;
    }

    public double resolvedAmbiguityConfidenceThreshold() {
        return ambiguityConfidenceThreshold != null ? ambiguityConfidenceThreshold : DEFAULT_AMBIGUITY_CONFIDENCE_THRESHOLD;
    }

    public int resolvedMaxHopDepth() {
        return maxHopDepth != null ? maxHopDepth : DEFAULT_MAX_HOP_DEPTH;
    }

    public int resolvedMaxUnresolvedCalls() {
        return maxUnresolvedCalls != null ? maxUnresolvedCalls : DEFAULT_MAX_UNRESOLVED_CALLS;
    }

    public List<String> resolvedFrameworkPrefixes() {
        return frameworkPrefixes != null && !frameworkPrefixes.isEmpty()
            ? frameworkPrefixes
            : DEFAULT_FRAMEWORK_PREFIXES;
    }
}
