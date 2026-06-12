package com.github.ehdez73.code2req.store;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TaskStoreSchema {
    private static final Logger log = LoggerFactory.getLogger(TaskStoreSchema.class);
    private final JdbcTemplate jdbc;

    public TaskStoreSchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() {
        createSchemaIfNotExists();
    }

    public void createSchemaIfNotExists() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS tasks (
                task_id TEXT PRIMARY KEY,
                file_path TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                content_type TEXT,
                content_hash TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
        log.info("Task store schema initialized");
    }

    public void dropTable() {
        jdbc.execute("DROP TABLE IF EXISTS tasks");
    }
}
