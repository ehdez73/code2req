package com.github.ehdez73.code2req.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskIdHasherTest {

    private TaskIdHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new TaskIdHasher();
    }

    @Test
    void deterministicHash() {
        String hash1 = hasher.hash("src/main/App.java", "abc123");
        String hash2 = hasher.hash("src/main/App.java", "abc123");
        assertEquals(hash1, hash2);
    }

    @Test
    void differentFilePathsProduceDifferentHashes() {
        String hash1 = hasher.hash("src/main/A.java", "abc123");
        String hash2 = hasher.hash("src/main/B.java", "abc123");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void differentContentHashesProduceDifferentHashes() {
        String hash1 = hasher.hash("src/main/App.java", "abc123");
        String hash2 = hasher.hash("src/main/App.java", "def456");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashHasCorrectLength() {
        String hash = hasher.hash("src/main/App.java", "abc123");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hashWithTestContentHash() {
        String withoutTest = hasher.hash("src/main/App.java", "abc123");
        String withTest = hasher.hash("src/main/App.java", "abc123", "testhash");
        assertNotEquals(withoutTest, withTest);
    }

    @Test
    void hashWithAllComponents() {
        String hash = hasher.hash("src/main/App.java", "abc123", "testhash", "gpt-4", "1.0");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void nullComponentsTreatedAsEmpty() {
        String withNulls = hasher.hash("f.java", "c", null, null, null);
        String withEmpties = hasher.hash("f.java", "c", "", "", "");
        assertEquals(withNulls, withEmpties);
    }
}
