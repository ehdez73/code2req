package com.github.ehdez73.code2req.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ManifestValidatorTest {

    private ManifestLoader manifestLoader;
    private ManifestValidator validator;

    @BeforeEach
    void setUp() {
        manifestLoader = new ManifestLoader();
        validator = new ManifestValidator(manifestLoader);
    }

    @Test
    void validManifestPasses(@TempDir Path tempDir) throws IOException {
        Path dir = createDir(tempDir, "valid-target");
        Path manifest = writeManifest(tempDir, """
            targets:
              - name: my-service
                path: %s
            """.formatted(dir.toAbsolutePath()));

        ManifestValidationResult result = validator.validate(manifest);
        assertFalse(result.hasErrors(), "Expected no errors but got: " + result.getErrors());
    }

    @Test
    void missingTargetsField(@TempDir Path tempDir) throws IOException {
        Path manifest = writeManifest(tempDir, "other: value");

        ManifestValidationResult result = validator.validate(manifest);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("targets")));
    }

    @Test
    void emptyTargetsList(@TempDir Path tempDir) throws IOException {
        Path manifest = writeManifest(tempDir, "targets: []");

        ManifestValidationResult result = validator.validate(manifest);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("at least one target")));
    }

    @Test
    void targetMissingName(@TempDir Path tempDir) throws IOException {
        Path dir = createDir(tempDir, "some-dir");
        Path manifest = writeManifest(tempDir, """
            targets:
              - path: %s
            """.formatted(dir.toAbsolutePath()));

        ManifestValidationResult result = validator.validate(manifest);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("name")));
    }

    @Test
    void targetMissingPath(@TempDir Path tempDir) throws IOException {
        Path manifest = writeManifest(tempDir, """
            targets:
              - name: my-service
            """);

        ManifestValidationResult result = validator.validate(manifest);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("path")));
    }

    @Test
    void invalidYamlSyntax(@TempDir Path tempDir) throws IOException {
        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, "invalid: yaml: [\n  broken");

        ManifestValidationResult result = validator.validate(manifest);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("parse") || e.contains("YAML")));
    }

    @Test
    void unrecognizedTopLevelField(@TempDir Path tempDir) throws IOException {
        Path dir = createDir(tempDir, "target");
        Path manifest = writeManifest(tempDir, """
            targets:
              - name: my-service
                path: %s
            custom-option: some-value
            """.formatted(dir.toAbsolutePath()));

        ManifestValidationResult result = validator.validate(manifest);
        assertFalse(result.hasErrors());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("custom-option")));
    }

    @Test
    void unrecognizedTargetField(@TempDir Path tempDir) throws IOException {
        Path dir = createDir(tempDir, "target");
        Path manifest = writeManifest(tempDir, """
            targets:
              - name: my-service
                path: %s
                unknown-field: true
            """.formatted(dir.toAbsolutePath()));

        ManifestValidationResult result = validator.validate(manifest);
        assertFalse(result.hasErrors());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("unknown-field")));
    }

    @Test
    void nonExistentTargetPath(@TempDir Path tempDir) throws IOException {
        Path manifest = writeManifest(tempDir, """
            targets:
              - name: my-service
                path: /nonexistent/path
            """);

        ManifestValidationResult result = validator.validate(manifest);
        assertFalse(result.hasErrors());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("does not exist")));
    }

    @Test
    void unrecognizedOutputField(@TempDir Path tempDir) throws IOException {
        Path dir = createDir(tempDir, "target");
        Path manifest = writeManifest(tempDir, """
            targets:
              - name: my-service
                path: %s
            output:
              unknown-output-field: true
            """.formatted(dir.toAbsolutePath()));

        ManifestValidationResult result = validator.validate(manifest);
        assertFalse(result.hasErrors());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("unknown-output-field")));
    }

    @Test
    void manifestFileNotFound(@TempDir Path tempDir) throws IOException {
        Path nonExistent = tempDir.resolve("does-not-exist.yaml");

        ManifestValidationResult result = validator.validate(nonExistent);
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("not found")));
    }
    
    @Test
    void multipleTargetsMixedValidity(@TempDir Path tempDir) throws IOException {
        Path validDir = createDir(tempDir, "valid");
        Path manifest = writeManifest(tempDir, """
            targets:
              - name: valid-service
                path: %s
              - name: invalid-service
                path: /nonexistent/path
            """.formatted(validDir.toAbsolutePath()));

        ManifestValidationResult result = validator.validate(manifest);
        assertFalse(result.hasErrors());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("does not exist")));
        assertEquals(1, result.getWarnings().size());
    }

    private Path writeManifest(Path tempDir, String content) throws IOException {
        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, content);
        return manifest;
    }

    private Path createDir(Path tempDir, String name) throws IOException {
        return Files.createDirectory(tempDir.resolve(name));
    }
}
