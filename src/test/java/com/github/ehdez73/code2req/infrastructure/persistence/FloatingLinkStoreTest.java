package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FloatingLinkStoreTest {

    @TempDir
    Path tempDir;

    private FloatingLinkStore store;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("fl-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        store = new FloatingLinkStore(jdbc);
    }

    @Test
    void saveAllAndCount() {
        var links = List.of(
            new FloatingLinkInfo("GET", "http://example.com/api", false, "RestTemplate", "src/App.java", "doCall", null, 0.0, "PENDING"),
            new FloatingLinkInfo("POST", "${url}/endpoint", true, "WebClient", "src/Client.java", "sendData", "/api/target", 1.0, "RESOLVED")
        );
        store.saveAll(links);

        assertEquals(2, store.count());
    }

    @Test
    void findAll() {
        var links = List.of(
            new FloatingLinkInfo("GET", "/api/test", false, "RestTemplate", "src/T.java", "m", null, 0.0, "PENDING")
        );
        store.saveAll(links);

        var found = store.findAll();
        assertEquals(1, found.size());
        assertEquals("GET", found.getFirst().method());
        assertEquals("/api/test", found.getFirst().urlPattern());
    }

    @Test
    void deleteAll() {
        store.saveAll(List.of(
            new FloatingLinkInfo("GET", "/a", false, "RT", "s.java", "m", null, 0.0, "PENDING")
        ));
        store.deleteAll();
        assertEquals(0, store.count());
    }
}