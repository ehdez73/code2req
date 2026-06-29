package com.github.ehdez73.code2req.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "code2req.execution")
public record ExecutionConfig(
    Integer maxConcurrentLlmCalls,
    Integer maxDiscoveryDepth,
    Double semanticValidationSampleRate,
    Integer llmUnresolvedThreshold,
    Integer maxInvestigationStepsPerFlow,
    Integer maxTokensPerRun,
    Double ambiguityConfidenceThreshold,
    List<String> testSuffixes,
    ExecutionMode executionMode,
    Boolean strictResponseFormat
) {
    public int resolvedUnresolvedThreshold() {
        return llmUnresolvedThreshold != null ? llmUnresolvedThreshold : 5;
    }

    public List<String> resolvedTestSuffixes() {
        return testSuffixes != null && !testSuffixes.isEmpty() ? testSuffixes : List.of("Test", "IT");
    }

    public ExecutionMode resolvedExecutionMode() {
        return executionMode != null ? executionMode : ExecutionMode.ASYNC;
    }

    public boolean resolvedStrictResponseFormat() {
        return strictResponseFormat != null ? strictResponseFormat : true;
    }
}
