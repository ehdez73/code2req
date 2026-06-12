package com.github.ehdez73.code2req.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-schema-" + "${random.uuid}" + ".db"
})
class TaskStoreSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TaskStoreSchema taskStoreSchema;

    @BeforeEach
    void setUp() {
        taskStoreSchema.dropTable();
    }

    @AfterEach
    void tearDown() {
        taskStoreSchema.dropTable();
    }

    @Test
    void schemaIsCreated() {
        taskStoreSchema.createSchemaIfNotExists();
        var tables = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='tasks'");
        assertEquals(1, tables.size());
        assertEquals("tasks", tables.getFirst().get("name"));
    }

    @Test
    void schemaIsIdempotent() {
        taskStoreSchema.createSchemaIfNotExists();
        taskStoreSchema.createSchemaIfNotExists();
        var tables = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='tasks'");
        assertEquals(1, tables.size());
    }

    @Test
    void schemaHasExpectedColumns() {
        taskStoreSchema.createSchemaIfNotExists();
        var columns = jdbc.queryForList("PRAGMA table_info(tasks)");
        var columnNames = columns.stream()
            .map(row -> (String) row.get("name"))
            .toList();
        assertTrue(columnNames.contains("task_id"));
        assertTrue(columnNames.contains("file_path"));
        assertTrue(columnNames.contains("status"));
        assertTrue(columnNames.contains("content_type"));
        assertTrue(columnNames.contains("content_hash"));
        assertTrue(columnNames.contains("created_at"));
        assertTrue(columnNames.contains("updated_at"));
    }
}
