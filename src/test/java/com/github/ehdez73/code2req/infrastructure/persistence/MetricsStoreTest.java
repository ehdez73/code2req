package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.common.domain.Metric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MetricsStoreTest {

    @TempDir
    Path tempDir;

    private MetricsStore store;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("metrics-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        store = new MetricsStore(jdbc);
    }

    @Test
    void saveAndCount() {
        store.save(new Metric("run-1", 1));
        assertEquals(1, store.count());
    }

    @Test
    void getLatestForPhase() {
        store.save(new Metric("run-1", 1, 10, 8, 15, 2, 3, 5, 0, 0.0, "2026-06-16T10:00:00"));
        store.save(new Metric("run-2", 1, 20, 18, 30, 4, 6, 10, 0, 0.0, "2026-06-16T11:00:00"));

        var latest = store.getLatestForPhase(1);
        assertNotNull(latest);
        assertEquals("run-2", latest.runId());
        assertEquals(20, latest.tasksTotal());
    }

    @Test
    void getLatestForMissingPhase() {
        var result = store.getLatestForPhase(99);
        assertNull(result);
    }

    @Test
    void deleteAll() {
        store.save(new Metric("run-1", 1));
        store.deleteAll();
        assertEquals(0, store.count());
    }
}