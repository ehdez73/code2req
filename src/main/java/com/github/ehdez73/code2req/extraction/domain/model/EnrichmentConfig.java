package com.github.ehdez73.code2req.extraction.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for LLM-based semantic enrichment.
 *
 * @param maxConcurrentLlmCalls Maximum number of concurrent LLM enrichment calls
 * @param executionMode         Execution mode: ASYNC or SYNC
 * @param strictResponseFormat  Enforce strict JSON response format from the LLM
 * @param maxTokensPerRun       Maximum tokens consumed per enrichment run
 */
@ConfigurationProperties(prefix = "code2req.enrichment")
public record EnrichmentConfig(
    Integer maxConcurrentLlmCalls,
    ExecutionMode executionMode,
    Boolean strictResponseFormat,
    Integer maxTokensPerRun
) {
    public int resolvedMaxConcurrentLlmCalls() {
        return maxConcurrentLlmCalls != null ? maxConcurrentLlmCalls : 5;
    }

    public ExecutionMode resolvedExecutionMode() {
        return executionMode != null ? executionMode : ExecutionMode.ASYNC;
    }

    public boolean resolvedStrictResponseFormat() {
        return strictResponseFormat != null ? strictResponseFormat : true;
    }
}
