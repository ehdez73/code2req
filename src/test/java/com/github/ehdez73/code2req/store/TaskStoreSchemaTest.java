package com.github.ehdez73.code2req.store;

import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-schema-" + "${random.uuid}" + ".db"
})
class TaskStoreSchemaTest {

    @MockitoBean
    private AgentPlatform agentPlatform;

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
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
        var names = tables.stream().map(m -> (String) m.get("name")).toList();
        assertEquals(5, names.size(), "Expected 5 user tables, got: " + names);
        assertTrue(names.containsAll(List.of("tasks", "execution_findings", "topic_links", "floating_links", "metrics")));
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
    void tasksTableHasExpectedColumns() {
        taskStoreSchema.createSchemaIfNotExists();
        var columns = jdbc.queryForList("PRAGMA table_info(tasks)");
        var columnNames = columns.stream()
            .map(row -> (String) row.get("name"))
            .toList();
        assertTrue(columnNames.containsAll(List.of("task_id", "file_path", "status", "content_type", "content_hash", "target_name", "created_at", "updated_at")));
    }



    @Test
    void executionFindingsTableHasExpectedColumns() {
        taskStoreSchema.createSchemaIfNotExists();
        var columns = jdbc.queryForList("PRAGMA table_info(execution_findings)");
        var names = columns.stream().map(row -> (String) row.get("name")).toList();
        assertTrue(names.containsAll(List.of("id", "task_id", "finding_type", "finding_json", "resolved", "schema_version", "created_at")));
    }

    @Test
    void topicLinksTableHasExpectedColumns() {
        taskStoreSchema.createSchemaIfNotExists();
        var columns = jdbc.queryForList("PRAGMA table_info(topic_links)");
        var names = columns.stream().map(row -> (String) row.get("name")).toList();
        assertTrue(names.containsAll(List.of("id", "broker", "topic_or_queue", "producer_task_id", "consumer_task_id", "resolved_status", "confidence", "created_at")));
    }

    @Test
    void floatingLinksTableHasExpectedColumns() {
        taskStoreSchema.createSchemaIfNotExists();
        var columns = jdbc.queryForList("PRAGMA table_info(floating_links)");
        var names = columns.stream().map(row -> (String) row.get("name")).toList();
        assertTrue(names.containsAll(List.of("id", "method", "url_or_path", "is_expression", "source_task_id", "target_endpoint", "confidence", "resolved_status", "created_at")));
    }

    @Test
    void metricsTableHasExpectedColumns() {
        taskStoreSchema.createSchemaIfNotExists();
        var columns = jdbc.queryForList("PRAGMA table_info(metrics)");
        var names = columns.stream().map(row -> (String) row.get("name")).toList();
        assertTrue(names.containsAll(List.of("id", "run_id", "phase", "tasks_total", "tasks_completed", "edges_resolved", "edges_unresolved", "topic_links_resolved", "floating_links_registered", "recorded_at")));
    }

    @Test
    void dropAllTablesRemovesAll() {
        taskStoreSchema.createSchemaIfNotExists();
        taskStoreSchema.dropAllTables();
        var userTables = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'");
        assertTrue(userTables.isEmpty(), "Expected no user tables after dropAllTables, got: " + userTables);
    }
}
