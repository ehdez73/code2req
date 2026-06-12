package com.github.ehdez73.code2req.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OutputConfig(
    @JsonProperty("spec-dir") String specDir,
    @JsonProperty("index-file") String indexFile,
    @JsonProperty("db-path") String dbPath
) {
    public static OutputConfig defaultConfig() {
        return new OutputConfig("spec-output", "code-graph-index.json", ".code2req_cache.db");
    }
}
