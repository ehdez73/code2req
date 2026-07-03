package com.github.ehdez73.code2req.common.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code2req.output")
public record OutputConfig(
    String specDir,
    String indexFile,
    String extractionCacheFile,
    String dbPath
) {
}
