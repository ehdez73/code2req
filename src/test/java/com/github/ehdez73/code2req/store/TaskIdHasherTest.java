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
        String hash1 = hasher.hash("src/main/App.java", "abc123", "test-target");
        String hash2 = hasher.hash("src/main/App.java", "abc123", "test-target");
        assertEquals(hash1, hash2);
    }

    @Test
    void differentFilePathsProduceDifferentHashes() {
        String hash1 = hasher.hash("src/main/A.java", "abc123", "test-target");
        String hash2 = hasher.hash("src/main/B.java", "abc123", "test-target");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void differentContentHashesProduceDifferentHashes() {
        String hash1 = hasher.hash("src/main/App.java", "abc123", "test-target");
        String hash2 = hasher.hash("src/main/App.java", "def456", "test-target");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashHasCorrectLength() {
        String hash = hasher.hash("src/main/App.java", "abc123", "test-target");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hashWithTestContentHash() {
        String withoutTest = hasher.hash("src/main/App.java", "abc123", "test-target");
        String withTest = hasher.hash("src/main/App.java", "abc123", "testhash", "test-target");
        assertNotEquals(withoutTest, withTest);
    }

    @Test
    void hashWithAllComponents() {
        String hash = hasher.hash("src/main/App.java", "abc123", "testhash", "gpt-4", "1.0", "test-target");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void nullComponentsTreatedAsEmpty() {
        String withNulls = hasher.hash("f.java", "c", null, null, null, null);
        String withEmpties = hasher.hash("f.java", "c", "", "", "", "");
        assertEquals(withNulls, withEmpties);
    }

    @Test
    void differentTargetNamesProduceDifferentHashes() {
        String hash1 = hasher.hash("src/main/App.java", "abc123", "target-a");
        String hash2 = hasher.hash("src/main/App.java", "abc123", "target-b");
        assertNotEquals(hash1, hash2);
    }
}
