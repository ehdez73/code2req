package com.github.ehdez73.code2req.enrichment.domain.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Execution tuning knobs for the code2req pipeline phases.
 * <p>All properties are optional; defaults are applied via the {@code resolved*()} methods.</p>
 *
 * @param maxConcurrentLlmCalls        Max parallel LLM enrichment calls (Phase&nbsp;2). Default: {@code 5}.
 * @param maxDiscoveryDepth             How deep to traverse the call graph from entry points. Default: {@code 3}.
 * @param semanticValidationSampleRate  Fraction of enriched tasks validated semantically (0.0–1.0). Default: {@code 0.20}.
 * @param llmUnresolvedThreshold        Files with more unresolved signatures than this trigger LLM enrichment. Default: {@code 5}.
 * @param maxInvestigationStepsPerFlow  Max trace steps before a flow is considered "deep". Default: {@code 5}.
 * @param maxTokensPerRun               Token budget per LLM call. Default: {@code 500000}.
 * @param ambiguityConfidenceThreshold  Minimum confidence ratio {@code (resolved / total)} to keep a flow unquarantined. Default: {@code 0.3}.
 * @param testSuffixes                  File name suffixes treated as tests (comma-separated). Default: {@code Test,IT}.
 * @param executionMode                 ASYNC or SYNC for Phase&nbsp;2 enrichment. Default: {@code ASYNC}.
 * @param strictResponseFormat          Reject LLM responses that don't match the expected schema. Default: {@code true}.
 * @param phase3TimeoutMinutes          Max wall-clock minutes for Phase&nbsp;3 GOAP agent run. Default: {@code 60}.
 * @param unresolvedFlowCountThreshold  Max unresolved calls before a flow is quarantined. Default: {@code 3}.
 */
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
    Boolean strictResponseFormat,
    Integer phase3TimeoutMinutes,
    Integer unresolvedFlowCountThreshold
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

    public int resolvedPhase3TimeoutMinutes() {
        return phase3TimeoutMinutes != null ? phase3TimeoutMinutes : 60;
    }

    public int resolvedUnresolvedFlowCountThreshold() {
        return unresolvedFlowCountThreshold != null ? unresolvedFlowCountThreshold : 3;
    }
}
