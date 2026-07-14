package com.github.ehdez73.code2req.enrichment.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Phase 2 LLM-based semantic enrichment.
 *
 * @param maxConcurrentLlmCalls       Maximum number of concurrent LLM enrichment calls
 * @param llmUnresolvedThreshold      Number of unresolved elements before triggering LLM enrichment
 * @param executionMode               Execution mode: ASYNC or SYNC
 * @param strictResponseFormat        Enforce strict JSON response format from the LLM
 * @param semanticValidationSampleRate Fraction of enrichment results to validate (0.0–1.0)
 * @param maxTokensPerRun             Maximum tokens consumed per enrichment run
 */
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
