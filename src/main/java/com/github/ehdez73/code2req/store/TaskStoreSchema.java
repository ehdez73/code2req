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
                target_name TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS execution_findings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id TEXT NOT NULL REFERENCES tasks(task_id),
                finding_type TEXT NOT NULL,
                finding_json TEXT NOT NULL,
                resolved INTEGER NOT NULL DEFAULT 1,
                schema_version TEXT NOT NULL DEFAULT '1.0',
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS topic_links (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                broker TEXT NOT NULL,
                topic_or_queue TEXT NOT NULL,
                producer_task_id TEXT REFERENCES tasks(task_id),
                consumer_task_id TEXT REFERENCES tasks(task_id),
                resolved_status TEXT NOT NULL DEFAULT 'PENDING',
                confidence REAL DEFAULT 1.0,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS floating_links (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                method TEXT NOT NULL,
                url_or_path TEXT NOT NULL,
                is_expression INTEGER NOT NULL DEFAULT 0,
                source_task_id TEXT NOT NULL REFERENCES tasks(task_id),
                target_endpoint TEXT,
                confidence REAL,
                resolved_status TEXT NOT NULL DEFAULT 'PENDING',
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS metrics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL,
                phase INTEGER NOT NULL DEFAULT 1,
                tasks_total INTEGER DEFAULT 0,
                tasks_completed INTEGER DEFAULT 0,
                edges_resolved INTEGER DEFAULT 0,
                edges_unresolved INTEGER DEFAULT 0,
                topic_links_resolved INTEGER DEFAULT 0,
                floating_links_registered INTEGER DEFAULT 0,
                tokens_consumed INTEGER DEFAULT 0,
                api_cost_estimated REAL DEFAULT 0.0,
                recorded_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """);
        log.info("Task store schema initialized with 5 tables");
    }

    public void dropAllTables() {
        jdbc.execute("DROP TABLE IF EXISTS execution_findings");
        jdbc.execute("DROP TABLE IF EXISTS topic_links");
        jdbc.execute("DROP TABLE IF EXISTS floating_links");
        jdbc.execute("DROP TABLE IF EXISTS metrics");
        jdbc.execute("DROP TABLE IF EXISTS tasks");
    }

    public void dropTable() {
        dropAllTables();
    }
}
