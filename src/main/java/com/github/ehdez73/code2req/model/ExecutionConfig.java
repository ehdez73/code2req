package com.github.ehdez73.code2req.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutionConfig(
    @JsonProperty("max-concurrent-llm-calls") Integer maxConcurrentLlmCalls,
    @JsonProperty("max-discovery-depth") Integer maxDiscoveryDepth,
    @JsonProperty("semantic-validation-sample-rate") Double semanticValidationSampleRate
) {
    public static ExecutionConfig defaultConfig() {
        return new ExecutionConfig(5, 3, 0.20);
    }
}
