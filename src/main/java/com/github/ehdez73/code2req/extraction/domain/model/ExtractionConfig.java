package com.github.ehdez73.code2req.extraction.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code2req.extraction")
public record ExtractionConfig(
    Integer maxInvestigationStepsPerFlow,
    Integer phase3TimeoutMinutes
) {
    public int resolvedMaxInvestigationStepsPerFlow() {
        return maxInvestigationStepsPerFlow != null ? maxInvestigationStepsPerFlow : 5;
    }

    public int resolvedPhase3TimeoutMinutes() {
        return phase3TimeoutMinutes != null ? phase3TimeoutMinutes : 60;
    }
}
