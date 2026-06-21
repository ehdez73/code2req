package com.github.ehdez73.code2req.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScanTarget(
    @JsonProperty("name") String name,
    @JsonProperty("path") String path,
    @JsonProperty("layer") String layer,
    @JsonProperty("tech_profile") String techProfile,
    @JsonProperty("entry_points") List<String> entryPoints,
    @JsonProperty("exclude_patterns") List<String> excludePatterns,
    @JsonProperty("java_version") String javaVersion
) {
    public String javaVersionOrDefault() {
        return javaVersion != null ? javaVersion : "17";
    }
}
