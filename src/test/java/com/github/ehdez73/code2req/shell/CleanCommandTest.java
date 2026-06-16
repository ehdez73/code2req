package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import com.github.ehdez73.code2req.store.TopicLinkStore;
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
    private Path indexPath;

    @BeforeEach
    void setUp() throws IOException {
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
        var manifestLoader = new ManifestLoader();
        command = new CleanCommand(taskStore, executionFindingStore, topicLinkStore, floatingLinkStore, metricsStore, manifestLoader);

        specDir = tempDir.resolve("spec-output");
        indexPath = specDir.resolve("code-graph-index.json");
    }

    @Test
    void cleanWithEmptyStore() {
        String result = command.clean(null);
        assertTrue(result.contains("Rows removed:"), "Expected row counts in output");
        assertTrue(result.contains("Clean complete"));
    }

    @Test
    void cleanWithPopulatedStore() {
        taskStore.save(new Task("id1", "/src/App.java", TaskStatus.SUCCESS, "java", "h1"));
        taskStore.save(new Task("id2", "/src/Config.java", TaskStatus.SUCCESS, "java", "h2"));
        taskStore.save(new Task("id3", "/src/Controller.java", TaskStatus.FAILED, "java", "h3"));

        assertEquals(3, taskStore.count());

        String result = command.clean(null);

        assertEquals(0, taskStore.count());
        assertTrue(result.contains("3 tasks"), "Expected 3 tasks in output");
        assertTrue(result.contains("Clean complete"));
    }

    @Test
    void cleanWithCustomManifest() throws IOException {
        taskStore.save(new Task("id1", "/src/App.java", TaskStatus.SUCCESS, "java", "h1"));

        Files.createDirectories(specDir);
        Files.writeString(indexPath, "{\"test\": true}");

        String specDirStr = specDir.toAbsolutePath().toString().replace("\\", "/");
        Path manifestFile = tempDir.resolve("custom-manifest.yaml");
        String manifestYaml = String.format("""
            targets:
              - name: test
                path: %s
                layer: backend
                tech_profile: java-spring-legacy
                entry_points: []
                exclude_patterns: []

            output:
              spec-dir: %s
              index-file: code-graph-index.json
              db-path: .test.db
            """, tempDir.toAbsolutePath().toString().replace("\\", "/"), specDirStr);

        Files.writeString(manifestFile, manifestYaml);

        assertEquals(1, taskStore.count());
        assertTrue(Files.exists(indexPath));

        String result = command.clean(manifestFile.toAbsolutePath().toString());

        assertEquals(0, taskStore.count());
        assertFalse(Files.exists(indexPath));
        assertFalse(Files.exists(specDir));
        assertTrue(result.contains("1 tasks"), "Expected 1 task in output");
        assertTrue(result.contains("Clean complete"));
    }
}
