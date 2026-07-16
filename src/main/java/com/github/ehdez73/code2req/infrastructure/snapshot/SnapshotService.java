package com.github.ehdez73.code2req.infrastructure.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.common.domain.OutputConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OutputConfig outputConfig;
    private final String snapshotDir;
    private final String dbUrl;
    private final RefreshableDataSource refreshableDataSource;
    private JdbcTemplate jdbc;

    public SnapshotService(RefreshableDataSource refreshableDataSource,
                           OutputConfig outputConfig,
                           @Value("${code2req.snapshot.dir:./snapshots}") String snapshotDir,
                           @Value("${spring.datasource.url}") String dbUrl) {
        this.refreshableDataSource = refreshableDataSource;
        this.jdbc = new JdbcTemplate(refreshableDataSource);
        this.outputConfig = outputConfig;
        this.snapshotDir = snapshotDir;
        this.dbUrl = dbUrl;
    }

    public String createSnapshot(String name) {
        String effectiveName = (name == null || name.isBlank())
            ? "snapshot_" + Instant.now().toString()
                .replace(":", "").replace("-", "").substring(0, 15)
            : name;

        Path dir = Path.of(snapshotDir, effectiveName);

        try {
            if (Files.exists(dir)) {
                throw new IllegalArgumentException("Snapshot '" + effectiveName + "' already exists at " + dir.toAbsolutePath());
            }
            Files.createDirectories(dir);

            Path dbPath = dir.resolve("sqlite.db");

            jdbc.execute("VACUUM INTO '" + escapePath(dbPath) + "'");
            log.info("SQLite DB snapshot saved to {}", dbPath);

            Path cachePath = Path.of(outputConfig.specDir(), outputConfig.extractionCacheFile());
            if (Files.exists(cachePath)) {
                Files.copy(cachePath, dir.resolve("extraction-cache.json"), REPLACE_EXISTING);
                log.info("Extraction cache snapshot saved to {}", dir.resolve("extraction-cache.json"));
            }

            writeMetadata(dir, effectiveName);
            log.info("Snapshot metadata written for '{}'", effectiveName);

        } catch (IOException e) {
            throw new RuntimeException("Failed to create snapshot '" + effectiveName + "': " + e.getMessage(), e);
        }

        return effectiveName;
    }

    public void restore(String name) {
        Path dir = Path.of(snapshotDir, name);

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Snapshot '" + name + "' not found in " + snapshotDir);
        }

        Path snapshotDb = dir.resolve("sqlite.db");
        Path snapshotCache = dir.resolve("extraction-cache.json");

        if (!Files.exists(snapshotDb)) {
            throw new IllegalArgumentException("Snapshot '" + name + "' is missing sqlite.db");
        }

        try {
            var oldPool = refreshableDataSource.delegate();
            if (oldPool instanceof HikariDataSource hds && !hds.isClosed()) {
                log.info("Closing HikariCP connection pool for restore");
                hds.close();
            }

            Path liveDb = Path.of(dbUrl.replace("jdbc:sqlite:", ""));
            Files.copy(snapshotDb, liveDb, REPLACE_EXISTING);
            log.info("SQLite DB restored from snapshot '{}'", name);

            Path liveCache = Path.of(outputConfig.specDir(), outputConfig.extractionCacheFile());
            Files.createDirectories(liveCache.getParent());
            if (Files.exists(snapshotCache)) {
                Files.copy(snapshotCache, liveCache, REPLACE_EXISTING);
                log.info("Extraction cache restored from snapshot '{}'", name);
            }

            var newPool = new HikariDataSource();
            newPool.setJdbcUrl(dbUrl);
            newPool.setDriverClassName("org.sqlite.JDBC");
            newPool.setMaximumPoolSize(10);
            newPool.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;");
            newPool.setPoolName("post-restore-hikari-pool");

            refreshableDataSource.replaceDelegate(newPool);
            this.jdbc = new JdbcTemplate(refreshableDataSource);
            log.info("HikariCP connection pool recreated after restore");

        } catch (IOException e) {
            throw new RuntimeException("Failed to restore snapshot '" + name + "': " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listSnapshots() {
        Path dir = Path.of(snapshotDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isDirectory)
                .sorted()
                .forEach(s -> {
                    try {
                        snapshots.add(readMetadata(s));
                    } catch (IOException e) {
                        var fallback = new LinkedHashMap<String, Object>();
                        fallback.put("name", s.getFileName().toString());
                        fallback.put("error", "Cannot read metadata: " + e.getMessage());
                        snapshots.add(fallback);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to list snapshots: {}", e.getMessage());
        }
        return snapshots;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(Path dir) throws IOException {
        Path metaFile = dir.resolve("snapshot.json");
        if (Files.exists(metaFile)) {
            return MAPPER.readValue(metaFile.toFile(), LinkedHashMap.class);
        }
        var fallback = new LinkedHashMap<String, Object>();
        fallback.put("name", dir.getFileName().toString());
        fallback.put("date", "unknown");
        fallback.put("size", "unknown");
        return fallback;
    }

    private void writeMetadata(Path dir, String name) throws IOException {
        var meta = new LinkedHashMap<String, Object>();
        meta.put("name", name);
        meta.put("date", Instant.now().toString());
        meta.put("cli_version", "1.0.0-SNAPSHOT");

        var files = new LinkedHashMap<String, Object>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isRegularFile).forEach(f -> {
                try {
                    var finfo = new LinkedHashMap<String, Object>();
                    finfo.put("size", Files.size(f));
                    finfo.put("checksum", sha256(f));
                    files.put(f.getFileName().toString(), finfo);
                } catch (IOException e) {
                    log.warn("Failed to read file info for {}: {}", f, e.getMessage());
                }
            });
        }
        meta.put("files", files);

        MAPPER.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("snapshot.json").toFile(), meta);
    }

    private static String escapePath(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
        byte[] buf = new byte[8192];
        int n;
        try (InputStream is = Files.newInputStream(path)) {
            while ((n = is.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
