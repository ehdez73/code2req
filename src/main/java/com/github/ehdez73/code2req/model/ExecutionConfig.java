package com.github.ehdez73.code2req.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code2req.execution")
public record ExecutionConfig(
    Integer maxConcurrentLlmCalls,
    Integer maxDiscoveryDepth,
    Double semanticValidationSampleRate,
    Integer llmUnresolvedThreshold,
    Integer maxInvestigationStepsPerFlow,
    Integer maxTokensPerRun,
    Double ambiguityConfidenceThreshold
) {
    public int resolvedUnresolvedThreshold() {
        return llmUnresolvedThreshold != null ? llmUnresolvedThreshold : 5;
    }
}
