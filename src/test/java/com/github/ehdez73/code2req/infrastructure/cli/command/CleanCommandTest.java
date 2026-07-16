package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.common.domain.OutputConfig;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CleanCommandTest {

    @TempDir
    Path tempDir;

    private TaskStore taskStore;
    private CleanCommand command;
    private Path specDir;
    @BeforeEach
    void setUp() throws IOException {
        specDir = tempDir.resolve("spec-output");
        var dbPath = tempDir.resolve("clean-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        taskStore = new TaskStore(jdbc);
        var executionFindingStore = new ExecutionFindingStore(jdbc);
        var topicLinkStore = new TopicLinkStore(jdbc);
        var floatingLinkStore = new FloatingLinkStore(jdbc);
        var metricsStore = new MetricsStore(jdbc);
        command = new CleanCommand(taskStore, executionFindingStore, topicLinkStore, floatingLinkStore, metricsStore,
            new OutputConfig(specDir.toString(), null), jdbc);
    }

    @Test
    void cleanWithEmptyStore() {
        String result = command.clean();
        assertTrue(result.contains("Rows removed:"), "Expected row counts in output");
        assertTrue(result.contains("Clean complete"));
    }

    @Test
    void cleanWithPopulatedStore() {
        taskStore.save(new Task("id1", "/src/App.java", TaskStatus.INDEXED, "java", "h1", "test"));
        taskStore.save(new Task("id2", "/src/Config.java", TaskStatus.INDEXED, "java", "h2", "test"));
        taskStore.save(new Task("id3", "/src/Controller.java", TaskStatus.FAILED, "java", "h3", "test"));

        assertEquals(3, taskStore.count());

        String result = command.clean();

        assertEquals(0, taskStore.count());
        assertTrue(result.contains("3 tasks"), "Expected 3 tasks in output");
        assertTrue(result.contains("Clean complete"));
    }

    @Test
    void cleanWithCustomManifest() throws IOException {
        taskStore.save(new Task("id1", "/src/App.java", TaskStatus.INDEXED, "java", "h1", "test"));

        Files.createDirectories(specDir);

        Path manifestFile = tempDir.resolve("custom-manifest.yaml");
        String manifestYaml = String.format("""
            targets:
              - name: test
                path: %s
                layer: backend
                tech_profile: java-spring-legacy
                entry_points: []
                exclude_patterns: []
            """, tempDir.toAbsolutePath().toString().replace("\\", "/"));
        Files.writeString(manifestFile, manifestYaml);

        assertEquals(1, taskStore.count());

        String result = command.clean();

        assertEquals(0, taskStore.count());
        assertFalse(Files.exists(specDir));
        assertTrue(result.contains("1 tasks"), "Expected 1 task in output");
        assertTrue(result.contains("Clean complete"));
    }
}
