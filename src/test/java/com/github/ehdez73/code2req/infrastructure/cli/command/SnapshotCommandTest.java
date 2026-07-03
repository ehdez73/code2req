package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.common.domain.OutputConfig;
import com.github.ehdez73.code2req.infrastructure.snapshot.RefreshableDataSource;
import com.github.ehdez73.code2req.infrastructure.snapshot.SnapshotService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotCommandTest {

    @TempDir
    Path tempDir;

    private SnapshotCommand command;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("live.db");
        var liveDs = new HikariDataSource();
        liveDs.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        liveDs.setMaximumPoolSize(1);

        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(liveDs);
        jdbc.execute("CREATE TABLE test (id INTEGER)");
        jdbc.execute("INSERT INTO test VALUES (1)");

        var refreshableDs = new RefreshableDataSource(liveDs);
        var outputConfig = new OutputConfig(tempDir.toString(), "index.json", "extraction-cache.json", dbPath.toString());

        var service = new SnapshotService(
            refreshableDs, outputConfig,
            tempDir.resolve("snapshots").toString(),
            "jdbc:sqlite:" + dbPath.toAbsolutePath());

        command = new SnapshotCommand(service);
    }

    @Test
    void createReturnsSuccessMessage() {
        var result = command.snapshot("test-snap");
        assertTrue(result.contains("test-snap"));
        assertTrue(Files.exists(tempDir.resolve("snapshots").resolve("test-snap")));
    }

    @Test
    void listReturnsCreatedSnapshots() {
        command.snapshot("s1");
        command.snapshot("s2");

        var result = command.snapshotList();
        assertTrue(result.contains("s1"));
        assertTrue(result.contains("s2"));
    }

    @Test
    void restoreSucceedsAfterCreate() {
        command.snapshot("backup");
        var result = command.restore("backup");
        assertTrue(result.contains("backup"));
    }

    @Test
    void restoreInvalidNameReturnsError() {
        var result = command.restore("ghost");
        assertTrue(result.contains("Error") || result.contains("not found"));
    }
}
