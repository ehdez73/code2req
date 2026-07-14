package com.github.ehdez73.code2req.infrastructure.config;

import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.common.domain.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ManifestLoaderTest {

    private final ManifestLoader loader = new ManifestLoader();

    @Test
    void loadValidManifest(@TempDir Path tempDir) throws IOException {
        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: my-service
                path: /some/path
                layer: backend
                tech_profile: java-spring
            """);

        ProjectManifest result = loader.load(manifest);
        assertEquals(1, result.targets().size());
        assertEquals("my-service", result.targets().getFirst().name());
        assertEquals("/some/path", result.targets().getFirst().path());
        assertEquals("backend", result.targets().getFirst().layer());
        assertEquals("java-spring", result.targets().getFirst().techProfile());
    }

    @Test
    void loadManifestWithMultipleTargets(@TempDir Path tempDir) throws IOException {
        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: service-a
                path: /path/a
              - name: service-b
                path: /path/b
              - name: service-c
                path: /path/c
            """);

        ProjectManifest result = loader.load(manifest);
        assertEquals(3, result.targets().size());
        assertEquals("service-a", result.targets().get(0).name());
        assertEquals("service-b", result.targets().get(1).name());
        assertEquals("service-c", result.targets().get(2).name());
    }

    @Test
    void loadManifestWithAllFields(@TempDir Path tempDir) throws IOException {
        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: my-service
                path: /some/path
                layer: backend
                tech_profile: java-spring-legacy
                entry_points:
                  - "com.example.Main"
                exclude_patterns:
                  - "**/generated/**"
                  - "**/*Pb.java"
            """);

        ProjectManifest result = loader.load(manifest);
        ScanTarget target = result.targets().getFirst();
        assertEquals("my-service", target.name());
        assertEquals("/some/path", target.path());
        assertEquals("backend", target.layer());
        assertEquals("java-spring-legacy", target.techProfile());
        assertNotNull(target.entryPoints());
        assertEquals(1, target.entryPoints().size());
        assertEquals("com.example.Main", target.entryPoints().getFirst());
        assertEquals(2, target.excludePatterns().size());
    }

    @Test
    void loadMalformedYamlThrowsException(@TempDir Path tempDir) {
        Path manifest = tempDir.resolve("manifest.yaml");
        assertThrows(IOException.class, () -> {
            Files.writeString(manifest, "invalid: yaml: [\nbroken");
            loader.load(manifest);
        });
    }
}
