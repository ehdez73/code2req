package com.github.ehdez73.code2req.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GenerateOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generateThrowsWhenCacheMissing() {
        var orchestrator = new GenerateOrchestrator(tempDir.resolve("nonexistent-cache.json"));
        var exception = assertThrows(IllegalStateException.class,
            () -> orchestrator.generate());
        assertTrue(exception.getMessage().contains("No extraction cache found"));
    }
}
