package com.github.ehdez73.code2req.extraction.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "code2req.extraction")
public record ExtractionConfig(
    Integer maxInvestigationStepsPerFlow,
    Integer phase3TimeoutMinutes,
    List<String> frameworkPrefixes
) {
    public static final List<String> DEFAULT_FRAMEWORK_PREFIXES = List.of("org.springframework.", "org.hibernate.");

    public int resolvedMaxInvestigationStepsPerFlow() {
        return maxInvestigationStepsPerFlow != null ? maxInvestigationStepsPerFlow : 5;
    }

    public int resolvedPhase3TimeoutMinutes() {
        return phase3TimeoutMinutes != null ? phase3TimeoutMinutes : 60;
    }

    public List<String> resolvedFrameworkPrefixes() {
        return frameworkPrefixes != null && !frameworkPrefixes.isEmpty()
            ? frameworkPrefixes
            : DEFAULT_FRAMEWORK_PREFIXES;
    }
}
