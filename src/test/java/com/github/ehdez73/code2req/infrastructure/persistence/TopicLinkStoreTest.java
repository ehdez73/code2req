package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopicLinkStoreTest {

    @TempDir
    Path tempDir;

    private TopicLinkStore store;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("tl-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        store = new TopicLinkStore(jdbc);
    }

    @Test
    void saveAllAndCount() {
        var links = List.of(
            TopicLink.resolved("KAFKA", "orders", "Producer", "p.java", "Consumer", "c.java"),
            TopicLink.orphanProducer("RABBITMQ", "queues", "Prod", "p2.java")
        );
        store.saveAll(links);

        assertEquals(2, store.count());
    }

    @Test
    void findByBroker() {
        store.saveAll(List.of(
            TopicLink.resolved("KAFKA", "orders", "P", "p.java", "C", "c.java"),
            TopicLink.resolved("KAFKA", "events", "P2", "p2.java", "C2", "c2.java"),
            TopicLink.orphanProducer("RABBITMQ", "q", "P3", "p3.java")
        ));

        var kafkaLinks = store.findByBroker("KAFKA");
        assertEquals(2, kafkaLinks.size());
    }

    @Test
    void findByStatus() {
        store.saveAll(List.of(
            TopicLink.resolved("KAFKA", "orders", "P", "p.java", "C", "c.java"),
            TopicLink.orphanProducer("KAFKA", "events", "P2", "p2.java")
        ));

        var resolved = store.findByStatus(TopicLink.STATUS_RESOLVED);
        assertEquals(1, resolved.size());

        var pending = store.findByStatus(TopicLink.STATUS_PENDING);
        assertEquals(1, pending.size());
    }

    @Test
    void emptyStore() {
        assertEquals(0, store.count());
    }
}