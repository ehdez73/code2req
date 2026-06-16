package com.github.ehdez73.code2req.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionFindingStoreTest {

    @TempDir
    Path tempDir;

    private ExecutionFindingStore store;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("ef-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        store = new ExecutionFindingStore(jdbc);
    }

    @Test
    void saveAndCount() {
        store.save("task-1", FindingType.COMPONENT, "{\"name\":\"Test\"}", true);
        store.save("task-1", FindingType.ENDPOINT, "{\"path\":\"/api\"}", true);
        store.save("task-2", FindingType.COMPONENT, "{\"name\":\"Other\"}", true);

        assertEquals(3, store.count());
        assertEquals(2, store.countByType(FindingType.COMPONENT));
        assertEquals(1, store.countByType(FindingType.ENDPOINT));
    }

    @Test
    void deleteByTaskId() {
        store.save("task-1", FindingType.COMPONENT, "{}", true);
        store.save("task-1", FindingType.ENDPOINT, "{}", true);
        store.save("task-2", FindingType.COMPONENT, "{}", true);

        store.deleteByTaskId("task-1");

        assertEquals(1, store.count());
    }

    @Test
    void deleteAll() {
        store.save("task-1", FindingType.COMPONENT, "{}", true);
        store.deleteAll();
        assertEquals(0, store.count());
    }

    @Test
    void countByTypeReturnsZeroForMissingType() {
        assertEquals(0, store.countByType("NONEXISTENT"));
    }
}