package com.github.ehdez73.code2req.infrastructure.cli.support;

import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
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
        assertTrue(result.contains("extract"));
    }

    @Test
    void suggestWithEnrichingTasks() {
        taskStore.save(new Task("t1", "/src/InProgress.java", TaskStatus.ENRICHING, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("ENRICHING"));
        assertTrue(result.contains("extract"));
    }

    @Test
    void suggestWithPendingTasks() {
        taskStore.save(new Task("t1", "/src/PendingDep.java", TaskStatus.PENDING, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("PENDING"));
        assertTrue(result.contains("extract"));
    }

    @Test
    void suggestWithIndexedTasks() {
        taskStore.save(new Task("t1", "/src/Ready.java", TaskStatus.INDEXED, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("1 task(s) ready"));
        assertTrue(result.contains("extract"));
        assertFalse(result.contains("not reached by any traced flow"));
    }

    @Test
    void suggestWithEnrichPendingTasks() {
        taskStore.save(new Task("t1", "/src/EnrichPending.java", TaskStatus.ENRICH_PENDING, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("1 task(s)"));
        assertTrue(result.contains("extract"));
        assertFalse(result.contains("not reached by any traced flow"));
    }

    @Test
    void suggestIndexedTasksAfterExtract() {
        taskStore.save(new Task("t1", "/src/NotReached.java", TaskStatus.INDEXED, "java", "h1", "test"));
        metricsStore.save(new Metric(UUID.randomUUID().toString(), 3,
            1, 1, 0, 0, 0, 0, 100, 0.0, LocalDateTime.now().toString()));

        String result = suggestionService.suggest();
        assertTrue(result.contains("not reached by any traced flow"));
        assertFalse(result.contains("extract"));
    }

    @Test
    void suggestEnrichPendingAfterExtract() {
        taskStore.save(new Task("t1", "/src/EnrichPending.java", TaskStatus.ENRICH_PENDING, "java", "h1", "test"));
        metricsStore.save(new Metric(UUID.randomUUID().toString(), 3,
            1, 1, 0, 0, 0, 0, 100, 0.0, LocalDateTime.now().toString()));

        String result = suggestionService.suggest();
        assertTrue(result.contains("not reached by any traced flow"));
        assertFalse(result.contains("extract"));
    }

    @Test
    void suggestMixedIndexedAndEnrichPendingAfterExtract() {
        taskStore.save(new Task("t1", "/src/NotReached.java", TaskStatus.INDEXED, "java", "h1", "test"));
        taskStore.save(new Task("t2", "/src/NotReached2.java", TaskStatus.ENRICH_PENDING, "java", "h2", "test"));
        metricsStore.save(new Metric(UUID.randomUUID().toString(), 3,
            1, 1, 0, 0, 0, 0, 100, 0.0, LocalDateTime.now().toString()));

        String result = suggestionService.suggest();
        assertTrue(result.contains("2 task(s) were not reached"));
        assertFalse(result.contains("extract"));
    }

    @Test
    void suggestAllEnrichedNoPhase3() {
        taskStore.save(new Task("t1", "/src/Done.java", TaskStatus.ENRICHED, "java", "h1", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("All tasks processed"));
        assertTrue(result.contains("extract"));
    }

    @Test
    void suggestAllEnrichedWithPhase3AlreadyRun() {
        taskStore.save(new Task("t1", "/src/Done.java", TaskStatus.ENRICHED, "java", "h1", "test"));
        metricsStore.save(new Metric(UUID.randomUUID().toString(), 3,
            1, 1, 0, 0, 0, 0, 100, 0.0, LocalDateTime.now().toString()));

        String result = suggestionService.suggest();
        assertTrue(result.contains("Pipeline complete"));
        assertTrue(result.contains("generate"));
        assertTrue(result.contains("clean"));
    }

    @Test
    void suggestWithMixedStates() {
        taskStore.save(new Task("f1", "/src/Failed.java", TaskStatus.FAILED, "java", "h1", "test"));
        taskStore.save(new Task("i1", "/src/Indexed.java", TaskStatus.INDEXED, "java", "h2", "test"));
        taskStore.save(new Task("i2", "/src/Indexed2.java", TaskStatus.INDEXED, "java", "h3", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("1 FAILED"));
        assertTrue(result.contains("scan --resume"));
        assertTrue(result.contains("2 task(s) ready"));
        assertTrue(result.contains("extract"));
    }

    @Test
    void suggestEnrichPendingBlockedByOtherStates() {
        taskStore.save(new Task("t1", "/src/Ready.java", TaskStatus.ENRICH_PENDING, "java", "h1", "test"));
        taskStore.save(new Task("t2", "/src/Stuck.java", TaskStatus.ENRICHING, "java", "h2", "test"));

        String result = suggestionService.suggest();
        assertTrue(result.contains("ENRICHING"));
        assertTrue(result.contains("extract"));
        assertFalse(result.contains("waiting for"));
    }

    @Test
    void suggestPipelineComplete() {
        taskStore.save(new Task("t1", "/src/Enriched.java", TaskStatus.ENRICHED, "java", "h1", "test"));
        metricsStore.save(new Metric(UUID.randomUUID().toString(), 3,
            1, 1, 0, 0, 0, 0, 100, 0.0, LocalDateTime.now().toString()));

        String result = suggestionService.suggest();
        assertTrue(result.contains("Pipeline complete"));
        assertTrue(result.contains("generate"));
        assertTrue(result.contains("clean"));
    }
}
