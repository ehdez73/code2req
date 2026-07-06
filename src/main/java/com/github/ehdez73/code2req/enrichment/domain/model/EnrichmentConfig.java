package com.github.ehdez73.code2req.enrichment.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code2req.enrichment")
public record EnrichmentConfig(
    Integer maxConcurrentLlmCalls,
    Integer llmUnresolvedThreshold,
    ExecutionMode executionMode,
    Boolean strictResponseFormat,
    Double semanticValidationSampleRate,
    Integer maxTokensPerRun
) {
    public int resolvedMaxConcurrentLlmCalls() {
        return maxConcurrentLlmCalls != null ? maxConcurrentLlmCalls : 5;
    }

    public int resolvedLlmUnresolvedThreshold() {
        return llmUnresolvedThreshold != null ? llmUnresolvedThreshold : 5;
    }

    public ExecutionMode resolvedExecutionMode() {
        return executionMode != null ? executionMode : ExecutionMode.ASYNC;
    }

    public boolean resolvedStrictResponseFormat() {
        return strictResponseFormat != null ? strictResponseFormat : true;
    }
}
