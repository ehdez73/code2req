package com.github.ehdez73.code2req.infrastructure.persistence;

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
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("ef-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
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

    @Test
    void deleteByFilePathRemovesOnlyTargetFileFindings() {
        String file1 = "/src/A.java";
        String file2 = "/src/B.java";

        jdbc.update("""
            INSERT INTO tasks (task_id, file_path, status, content_type, content_hash, target_name)
            VALUES (?, ?, 'INDEXED', 'java', 'abc123', 'demo')
        """, "task-A1", file1);
        jdbc.update("""
            INSERT INTO tasks (task_id, file_path, status, content_type, content_hash, target_name)
            VALUES (?, ?, 'INDEXED', 'java', 'def456', 'demo')
        """, "task-B1", file2);

        store.save("task-A1", FindingType.COMPONENT, "{\"name\":\"A\"}", true);
        store.save("task-A1", FindingType.ENDPOINT, "{\"path\":\"/a\"}", true);
        store.save("task-B1", FindingType.COMPONENT, "{\"name\":\"B\"}", true);

        int deleted = store.deleteByFilePath(file1);

        assertEquals(2, deleted);
        assertEquals(1, store.count());
        assertEquals(1, store.countByType(FindingType.COMPONENT));
    }

    @Test
    void deleteByFilePathNoMatchingFileReturnsZero() {
        int deleted = store.deleteByFilePath("/nonexistent/File.java");
        assertEquals(0, deleted);
    }
}
