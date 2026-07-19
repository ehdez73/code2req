package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.extraction.domain.model.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserResponseStoreTest {

    @TempDir
    Path tempDir;

    private UserResponseStore store;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("ur-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        store = new UserResponseStore(jdbc);
    }

    @Test
    void saveAndFindBySessionId() {
        var response = new UserResponse("session-1", "What is this method?", "It is a payment gateway", "2026-07-19T10:00:00");
        store.save(response);

        List<UserResponse> found = store.findBySessionId("session-1");

        assertEquals(1, found.size());
        assertEquals("What is this method?", found.get(0).question());
        assertEquals("It is a payment gateway", found.get(0).answer());
    }

    @Test
    void findByQuestionReturnsLatestAnswer() {
        store.save(new UserResponse("s1", "Ambiguous call target?", "Stripe", "2026-07-19T10:00:00"));
        store.save(new UserResponse("s2", "Ambiguous call target?", "Paypal", "2026-07-19T10:00:01"));

        var found = store.findByQuestion("Ambiguous call target?");

        assertTrue(found.isPresent());
        assertEquals("Paypal", found.get().answer());
    }

    @Test
    void findByQuestionReturnsEmptyForUnknownQuestion() {
        var found = store.findByQuestion("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findBySessionIdReturnsEmptyForUnknownSession() {
        List<UserResponse> found = store.findBySessionId("unknown");
        assertTrue(found.isEmpty());
    }
}
