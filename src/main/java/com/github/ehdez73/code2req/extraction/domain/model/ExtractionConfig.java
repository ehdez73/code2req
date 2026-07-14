package com.github.ehdez73.code2req.extraction.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Phase 3 GOAP agent orchestration behaviour.
 *
 * @param maxInvestigationStepsPerFlow Maximum number of flow steps to trace per entry point
 * @param phase3TimeoutMinutes         Timeout in minutes for Phase 3 GOAP agent execution
 */
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
