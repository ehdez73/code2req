package com.github.ehdez73.code2req.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProjectManifest(
    @JsonProperty("targets") List<ScanTarget> targets,
    @JsonProperty("execution") ExecutionConfig executionConfig,
    @JsonProperty("output") OutputConfig outputConfig
) {
    public ExecutionConfig executionConfig() {
        return executionConfig != null ? executionConfig : ExecutionConfig.defaultConfig();
    }

    public OutputConfig outputConfig() {
        return outputConfig != null ? outputConfig : OutputConfig.defaultConfig();
    }
}
