package com.github.ehdez73.code2req.infrastructure.snapshot;

import com.github.ehdez73.code2req.common.domain.OutputConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotServiceTest {

    @TempDir
    Path tempDir;

    private SnapshotService service;
    private Path dbPath;
    private RefreshableDataSource refreshableDs;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("live.db");
        var liveDs = new HikariDataSource();
        liveDs.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        liveDs.setMaximumPoolSize(1);

        refreshableDs = new RefreshableDataSource(liveDs);

        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(liveDs);
        jdbc.execute("CREATE TABLE IF NOT EXISTS test (id INTEGER)");
        jdbc.execute("INSERT INTO test VALUES (1), (2), (3)");

        var indexFile = tempDir.resolve("code-graph-index.json");
        var cacheFile = tempDir.resolve("extraction-cache.json");
        try {
            Files.writeString(indexFile, "{\"version\":\"1.0\"}");
            Files.writeString(cacheFile, "{\"crossRefResult\":null}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var outputConfig = new OutputConfig(tempDir.toString(), "code-graph-index.json", "extraction-cache.json", dbPath.toString());

        service = new SnapshotService(
            refreshableDs, outputConfig,
            tempDir.resolve("snapshots").toString(),
            "jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    @Test
    void createSnapshotCreatesDirectoryWithFiles() {
        var name = service.createSnapshot("test-snapshot");

        var snapDir = tempDir.resolve("snapshots").resolve(name);
        assertTrue(Files.isDirectory(snapDir));
        assertTrue(Files.exists(snapDir.resolve("sqlite.db")));
        assertTrue(Files.exists(snapDir.resolve("code-graph-index.json")));
        assertTrue(Files.exists(snapDir.resolve("extraction-cache.json")));
        assertTrue(Files.exists(snapDir.resolve("snapshot.json")));
    }

    @Test
    void createSnapshotReturnsGeneratedNameWhenNull() {
        var name = service.createSnapshot(null);
        assertNotNull(name);
        assertTrue(name.startsWith("snapshot_"));
    }

    @Test
    void createExistingSnapshotThrows() {
        service.createSnapshot("dup");
        assertThrows(IllegalArgumentException.class, () -> service.createSnapshot("dup"));
    }

    @Test
    void listSnapshotsReturnsCreated() {
        service.createSnapshot("s1");
        service.createSnapshot("s2");

        var list = service.listSnapshots();
        assertEquals(2, list.size());
    }

    @Test
    void listSnapshotsReturnsEmptyWhenNoSnapshots() {
        assertTrue(service.listSnapshots().isEmpty());
    }

    @Test
    void restoreReplacesLiveDb() {
        service.createSnapshot("backup");

        var liveBefore = new org.springframework.jdbc.core.JdbcTemplate(
            ((HikariDataSource) refreshableDs.delegate()));
        var countBefore = liveBefore.queryForObject("SELECT COUNT(*) FROM test", Integer.class);
        assertEquals(3, countBefore);

        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
            ((HikariDataSource) refreshableDs.delegate()));
        jdbc.execute("DROP TABLE test");

        service.restore("backup");

        var liveAfter = new org.springframework.jdbc.core.JdbcTemplate(
            ((HikariDataSource) refreshableDs.delegate()));
        var countAfter = liveAfter.queryForObject("SELECT COUNT(*) FROM test", Integer.class);
        assertEquals(3, countAfter);
    }

    @Test
    void restoreInvalidNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.restore("nonexistent"));
    }
}
