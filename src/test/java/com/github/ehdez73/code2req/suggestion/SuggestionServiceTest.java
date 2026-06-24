package com.github.ehdez73.code2req.suggestion;

import com.github.ehdez73.code2req.model.Metric;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SuggestionServiceTest {

    @TempDir
    Path tempDir;

    private TaskStore taskStore;
    private MetricsStore metricsStore;
    private SuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("suggestion-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        taskStore = new TaskStore(jdbc);
        metricsStore = new MetricsStore(jdbc);
        suggestionService = new SuggestionService(taskStore, metricsStore);
    }

    @Test
    void suggestWithEmptyStore() {
        String result = suggestionService.suggest();
        assertTrue(result.contains("No tasks found"));
        assertTrue(result.contains("scan"));
    }

    @Test
    void suggestWithFailedTasks() {
        taskStore.save(new Task("f1", "/src/Broken.java", TaskStatus.FAILED, "java", "h1", "test"));
        taskStore.save(new Task("f2", "/src/Broken2.java", TaskStatus.FAILED, "java", "h2", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("2 FAILED"));
        assertTrue(result.contains("scan --resume"));
    }

    @Test
    void suggestWithEnrichFailedTasks() {
        taskStore.save(new Task("t1", "/src/FailedEnrich.java", TaskStatus.ENRICH_FAILED, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("ENRICH_FAILED"));
        assertTrue(result.contains("run --resume"));
    }

    @Test
    void suggestWithEnrichingTasks() {
        taskStore.save(new Task("t1", "/src/InProgress.java", TaskStatus.ENRICHING, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("ENRICHING"));
        assertTrue(result.contains("run --resume"));
    }

    @Test
    void suggestWithPendingTasks() {
        taskStore.save(new Task("t1", "/src/PendingDep.java", TaskStatus.PENDING, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("PENDING"));
        assertTrue(result.contains("run --resume"));
    }

    @Test
    void suggestWithIndexedTasks() {
        taskStore.save(new Task("t1", "/src/Ready.java", TaskStatus.INDEXED, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("1 INDEXED"));
        assertTrue(result.contains("plan"));
    }

    @Test
    void suggestWithEnrichPendingTasks() {
        taskStore.save(new Task("t1", "/src/EnrichPending.java", TaskStatus.ENRICH_PENDING, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("1 task(s) waiting"));
        assertTrue(result.contains("run"));
    }

    @Test
    void suggestAllEnrichedNoPhase3() {
        taskStore.save(new Task("t1", "/src/Done.java", TaskStatus.ENRICHED, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("All tasks enriched"));
        assertTrue(result.contains("Phase 3"));
        assertTrue(result.contains("run"));
    }

    @Test
    void suggestAllEnrichedWithPhase3AlreadyRun() {
        taskStore.save(new Task("t1", "/src/Done.java", TaskStatus.ENRICHED, "java", "h1", "test"));
        metricsStore.save(new Metric(UUID.randomUUID().toString(), 3,
            1, 1, 0, 0, 0, 0, 100, 0.0, LocalDateTime.now().toString()));

        String result = suggestionService.suggest();
        assertTrue(result.contains("No actionable tasks"));
    }

    @Test
    void suggestWithMixedStates() {
        taskStore.save(new Task("f1", "/src/Failed.java", TaskStatus.FAILED, "java", "h1", "test"));
        taskStore.save(new Task("i1", "/src/Indexed.java", TaskStatus.INDEXED, "java", "h2", "test"));
        taskStore.save(new Task("i2", "/src/Indexed2.java", TaskStatus.INDEXED, "java", "h3", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("1 FAILED"));
        assertTrue(result.contains("scan --resume"));
        assertTrue(result.contains("2 INDEXED"));
        assertTrue(result.contains("plan"));
    }

    @Test
    void suggestEnrichPendingBlockedByOtherStates() {
        taskStore.save(new Task("t1", "/src/Ready.java", TaskStatus.ENRICH_PENDING, "java", "h1", "test"));
        taskStore.save(new Task("t2", "/src/Stuck.java", TaskStatus.ENRICHING, "java", "h2", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("ENRICHING"));
        assertTrue(result.contains("run --resume"));
        assertFalse(result.contains("waiting for enrichment"));
    }

    @Test
    void suggestNothingActionable() {
        taskStore.save(new Task("t1", "/src/Enriched.java", TaskStatus.ENRICHED, "java", "h1", "test"));
        metricsStore.save(new Metric(UUID.randomUUID().toString(), 3,
            1, 1, 0, 0, 0, 0, 100, 0.0, LocalDateTime.now().toString()));

        String result = suggestionService.suggest();
        assertTrue(result.contains("No actionable tasks"));
    }
}
