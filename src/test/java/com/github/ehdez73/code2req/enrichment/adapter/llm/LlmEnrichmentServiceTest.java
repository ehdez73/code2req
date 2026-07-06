package com.github.ehdez73.code2req.enrichment.adapter.llm;

import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.TestAssertionExtractor;
import com.github.ehdez73.code2req.enrichment.domain.model.EnrichmentConfig;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.enrichment.domain.model.PlannerDecision;
import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class LlmEnrichmentServiceTest {

    @TempDir
    Path tempDir;

    private TaskStore taskStore;
    private ExecutionFindingStore findingStore;
    private LlmEnrichmentService executor;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("executor-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        taskStore = new TaskStore(jdbc);
        findingStore = new ExecutionFindingStore(jdbc);

        var budgetCalculator = new ContextBudgetCalculator();
        var simulationStub = new SimulationStub();

        var enrichmentConfig = new EnrichmentConfig(null, null, null, null, null, null);
        var txManager = new DataSourceTransactionManager(ds);
        var txTemplate = new TransactionTemplate(txManager);
        executor = new LlmEnrichmentService(null, findingStore, taskStore,
            budgetCalculator, simulationStub, null, null, new TestAssertionExtractor(),
            enrichmentConfig, null, txTemplate);
    }

    @Test
    void enrichWithDryRunReturnsValidResult() throws Exception {
        var task = new Task("test-task-1", "/src/test.java",
            TaskStatus.PENDING, "test-module", "abc123", "test");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-1", "/src/test.java", "test",
            List.of(QualificationReason.SPRING_DATA_INTERFACE));

        CompletableFuture<ExecutionFinding> future = executor.enrich(
            task, decision, "class Test {}", null, null, true);

        ExecutionFinding result = future.get();
        assertNotNull(result);
        assertEquals("test-task-1", result.metadata().taskId());
        assertEquals("/src/test.java", result.metadata().filePath());
        assertNotNull(result.businessAbstraction());
        assertNotNull(result.businessAbstraction().purpose());
        assertNotNull(result.businessRulesAndGuardrails());
        assertNotNull(result.architecturalConnections());
    }

    @Test
    void enrichWithDryRunPersistsFinding() throws Exception {
        var task = new Task("test-task-2", "/src/service/OrderService.java",
            TaskStatus.PENDING, "test-module", "def456", "test");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-2", "/src/service/OrderService.java", "test",
            List.of(QualificationReason.NATIVE_SQL_QUERY));

        executor.enrich(task, decision, "class OrderService {}", null, null, true).get();

        assertEquals(1, findingStore.countByType(FindingType.SEMANTIC_ENRICHMENT));
    }

    @Test
    void enrichWithDryRunUpdatesTaskStatusToEnriched() throws Exception {
        var task = new Task("test-task-3", "/src/Test.java",
            TaskStatus.PENDING, "test-module", "ghi789", "test");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-3", "/src/Test.java", "test",
            List.of(QualificationReason.SCHEDULED_TASK_PRESENT));

        executor.enrich(task, decision, "class Test {}", null, null, true).get();

        var saved = taskStore.findById("test-task-3");
        assertTrue(saved.isPresent());
        assertEquals(TaskStatus.ENRICHED, saved.get().status());
    }

    @Test
    void enrichWithStructuralContext() throws Exception {
        var task = new Task("test-task-4", "/src/Test.java",
            TaskStatus.PENDING, "test-module", "jkl012", "test");
        taskStore.save(task);

        String structuralContext = """
            {
                "call_graph_edges": [
                    {"source": "Test.methodA", "target": "Other.methodB", "resolved": true}
                ],
                "endpoints": [{"path": "/api/test", "method": "GET"}]
            }
            """;

        var decision = PlannerDecision.qualified("test-task-4", "/src/Test.java", "test",
            List.of(QualificationReason.UNRESOLVED_SIGNATURES_EXCEEDED));

        CompletableFuture<ExecutionFinding> future = executor.enrich(
            task, decision, "class Test {}", null, structuralContext, true);

        ExecutionFinding result = future.get();
        assertNotNull(result);
        assertEquals("test-task-4", result.metadata().taskId());
    }

    @Test
    void enrichWithNullSourceSucceedsInDryRun() throws Exception {
        var task = new Task("test-task-5", "/src/Nullable.java",
            TaskStatus.PENDING, "test-module", "mno345", "test");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-5", "/src/Nullable.java", "test",
            List.of(QualificationReason.SPRING_DATA_INTERFACE));

        CompletableFuture<ExecutionFinding> future = executor.enrich(
            task, decision, null, null, null, true);

        ExecutionFinding result = future.get();
        assertNotNull(result);
        assertEquals(TaskStatus.ENRICHED, taskStore.findById("test-task-5").get().status());
    }
}
