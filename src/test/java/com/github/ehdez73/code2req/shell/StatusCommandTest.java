package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.model.Metric;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatusCommandTest {

    @TempDir
    Path tempDir;

    private TaskStore taskStore;
    private MetricsStore metricsStore;
    private StatusCommand command;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("status-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        taskStore = new TaskStore(jdbc);
        metricsStore = new MetricsStore(jdbc);
        command = new StatusCommand(taskStore, metricsStore);
    }

    @Test
    void statusWithEmptyStore() {
        String result = command.status(null, false);
        assertTrue(result.contains("Total: 0"));
        assertTrue(result.contains("PENDING: 0"));
        assertTrue(result.contains("ENRICH_PENDING: 0"));
        assertTrue(result.contains("INDEXED: 0"));
        assertTrue(result.contains("FAILED: 0"));
    }

    @Test
    void statusWithMixedStatuses() {
        taskStore.save(new Task("id1", "/src/App.java", TaskStatus.INDEXED, "java", "h1", "test"));
        taskStore.save(new Task("id2", "/src/Config.java", TaskStatus.INDEXED, "java", "h2", "test"));
        taskStore.save(new Task("id3", "/src/Controller.java", TaskStatus.FAILED, "java", "h3", "test"));
        taskStore.save(new Task("id4", "/src/Service.java", TaskStatus.PENDING, "java", "h4", "test"));
        taskStore.save(new Task("id5", "/src/Repo.java", TaskStatus.ENRICHING, "java", "h5", "test"));

        String result = command.status(null, false);
        assertTrue(result.contains("Total: 5"));
        assertTrue(result.contains("PENDING: 1"));
        assertTrue(result.contains("ENRICHING: 1"));
        assertTrue(result.contains("INDEXED: 2"));
        assertTrue(result.contains("FAILED: 1"));
    }

    @Test
    void statusWithFilter() {
        taskStore.save(new Task("id1", "/src/App.java", TaskStatus.INDEXED, "java", "h1", "test"));
        taskStore.save(new Task("id2", "/src/Config.java", TaskStatus.FAILED, "java", "h2", "test"));
        taskStore.save(new Task("id3", "/src/Controller.java", TaskStatus.FAILED, "java", "h3", "test"));

        String result = command.status("FAILED", false);
        assertTrue(result.contains("FAILED: 2"));
        assertFalse(result.contains("INDEXED"));
        assertFalse(result.contains("Total:"));
    }

    @Test
    void statusWithInvalidFilter() {
        String result = command.status("NONEXISTENT", false);
        assertTrue(result.contains("Error: Invalid status"));
    }

    @Test
    void statusVerbose() {
        taskStore.save(new Task("id1", "/src/App.java", TaskStatus.INDEXED, "java", "h1", "test"));
        taskStore.save(new Task("id2", "/src/Config.java", TaskStatus.INDEXED, "java", "h2", "test"));

        String result = command.status(null, true);
        assertTrue(result.contains("/src/App.java"));
        assertTrue(result.contains("/src/Config.java"));
        assertTrue(result.contains("INDEXED: 2"));
    }

    @Test
    void statusShowsNoPhase2MetricsWhenNoneRecorded() {
        String result = command.status(null, false);
        assertTrue(result.contains("Phase 2"));
        assertTrue(result.contains("No Phase 2 run data available"));
    }

    @Test
    void statusShowsPhase2MetricsWhenRecorded() {
        metricsStore.save(new Metric(
            UUID.randomUUID().toString(), 2,
            10, 8, 0, 0, 0, 0,
            1500, 0.003, LocalDateTime.now().toString()));

        String result = command.status(null, false);
        assertTrue(result.contains("Tokens consumed: 1500"));
        assertTrue(result.contains("$0.003"));
        assertTrue(result.contains("Tasks completed: 8"));
    }

    @Test
    void statusShowsPhase3MetricsWhenRecorded() {
        metricsStore.save(new Metric(
            UUID.randomUUID().toString(), 3,
            5, 5, 0, 0, 0, 0,
            500, 0.0, LocalDateTime.now().toString()));

        String result = command.status(null, false);
        assertTrue(result.contains("Phase 3"));
        assertTrue(result.contains("Tokens consumed: 500"));
        assertTrue(result.contains("Tasks completed: 5"));
    }
}
