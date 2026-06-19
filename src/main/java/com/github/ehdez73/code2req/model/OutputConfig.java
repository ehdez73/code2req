package com.github.ehdez73.code2req.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "code2req.output")
public record OutputConfig(
    String specDir,
    String indexFile,
    String dbPath
) {
}
