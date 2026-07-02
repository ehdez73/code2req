package com.github.ehdez73.code2req.infrastructure.cli.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GenerateCommandTest {

    @TempDir
    Path tempDir;

    private GenerateCommand command;

    @BeforeEach
    void setUp() {
        var orch = new com.github.ehdez73.code2req.generation.GenerateOrchestrator(
            tempDir.toString(), "extraction-cache.json");
        command = new GenerateCommand(orch);
    }

    @Test
    void missingCacheReturnsError() {
        var result = command.generate();
        assertTrue(result.contains("Error") || result.contains("no extraction cache"));
    }

    @Test
    void validCacheProducesSpecFiles() throws Exception {
        var cacheFile = tempDir.resolve("extraction-cache.json");
        Files.writeString(cacheFile, """
            {"crossRefResult":{"features":[],"flowNames":[],"crossFlowRelationships":[]},"orphanedMethods":[],"quarantineGaps":[]}
            """);

        command.generate();
        assertTrue(Files.exists(tempDir.resolve("spec.md")));
        assertTrue(Files.exists(tempDir.resolve("semantic_manifest.json")));
    }

    @Test
    void incompatibleCacheFormatReturnsError() throws Exception {
        var cacheFile = tempDir.resolve("extraction-cache.json");
        Files.writeString(cacheFile, "not valid json");

        var result = command.generate();
        assertTrue(result.contains("Error") || result.contains("incompatible format"));
    }
}
