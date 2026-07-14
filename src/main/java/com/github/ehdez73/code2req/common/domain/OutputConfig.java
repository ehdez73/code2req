package com.github.ehdez73.code2req.common.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for output file paths and names.
 *
 * @param specDir              Output directory for generated specification documents
 * @param indexFile            Filename for the code graph index JSON export
 * @param extractionCacheFile  Filename for the extraction cache JSON
 * @param dbPath               Path to the SQLite database file
 */
@ConfigurationProperties(prefix = "code2req.output")
public record OutputConfig(
    String specDir,
    String indexFile,
    String extractionCacheFile,
    String dbPath
) {
}
