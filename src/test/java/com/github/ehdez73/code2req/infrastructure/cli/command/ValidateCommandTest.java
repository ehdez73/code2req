package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.infrastructure.config.ManifestLoader;
import com.github.ehdez73.code2req.infrastructure.config.ManifestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ValidateCommandTest {

    @TempDir
    Path tempDir;

    private ValidateCommand command;

    @BeforeEach
    void setUp() {
        var loader = new ManifestLoader();
        var validator = new ManifestValidator(loader);
        command = new ValidateCommand(validator);
    }

    @Test
    void validManifestReturnsPassed() throws IOException {
        var manifest = tempDir.resolve("project-manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: test-app
                path: /tmp/src
                layer: backend
                tech_profile: java-spring-legacy
            """);

        var result = command.validate(manifest.toString());
        assertTrue(result.contains("Validation passed") || result.contains("passed"));
    }

    @Test
    void missingManifestReturnsError() {
        var result = command.validate(tempDir.resolve("nonexistent.yaml").toString());
        assertTrue(result.contains("Error") || result.contains("not found"));
    }

    @Test
    void malformedYamlReturnsError() throws IOException {
        var manifest = tempDir.resolve("bad.yaml");
        Files.writeString(manifest, "targets: [unclosed list");

        var result = command.validate(manifest.toString());
        assertTrue(result.contains("Error") || result.contains("failed"));
    }
}
