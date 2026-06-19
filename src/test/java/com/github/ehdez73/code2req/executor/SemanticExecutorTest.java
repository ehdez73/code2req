package com.github.ehdez73.code2req.executor;

import com.github.ehdez73.code2req.model.ExecutionFinding;
import com.github.ehdez73.code2req.model.PlannerDecision;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FindingType;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SemanticExecutorTest {

    @TempDir
    Path tempDir;

    private TaskStore taskStore;
    private ExecutionFindingStore findingStore;
    private SemanticExecutor executor;

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

        executor = new SemanticExecutor(null, findingStore, taskStore,
            budgetCalculator, simulationStub);
    }

    @Test
    void enrichWithDryRunReturnsValidResult() throws Exception {
        var task = new Task("test-task-1", "/src/test.java",
            TaskStatus.PENDING, "test-module", "abc123");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-1", "/src/test.java",
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
            TaskStatus.PENDING, "test-module", "def456");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-2", "/src/service/OrderService.java",
            List.of(QualificationReason.NATIVE_SQL_QUERY));

        executor.enrich(task, decision, "class OrderService {}", null, null, true).get();

        assertEquals(1, findingStore.countByType(FindingType.SEMANTIC_ENRICHMENT));
    }

    @Test
    void enrichWithDryRunUpdatesTaskStatusToSuccess() throws Exception {
        var task = new Task("test-task-3", "/src/Test.java",
            TaskStatus.PENDING, "test-module", "ghi789");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-3", "/src/Test.java",
            List.of(QualificationReason.SCHEDULED_TASK_PRESENT));

        executor.enrich(task, decision, "class Test {}", null, null, true).get();

        var saved = taskStore.findById("test-task-3");
        assertTrue(saved.isPresent());
        assertEquals(TaskStatus.SUCCESS, saved.get().status());
    }

    @Test
    void enrichWithStructuralContext() throws Exception {
        var task = new Task("test-task-4", "/src/Test.java",
            TaskStatus.PENDING, "test-module", "jkl012");
        taskStore.save(task);

        String structuralContext = """
            {
                "call_graph_edges": [
                    {"source": "Test.methodA", "target": "Other.methodB", "resolved": true}
                ],
                "endpoints": [{"path": "/api/test", "method": "GET"}]
            }
            """;

        var decision = PlannerDecision.qualified("test-task-4", "/src/Test.java",
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
            TaskStatus.PENDING, "test-module", "mno345");
        taskStore.save(task);

        var decision = PlannerDecision.qualified("test-task-5", "/src/Nullable.java",
            List.of(QualificationReason.SPRING_DATA_INTERFACE));

        CompletableFuture<ExecutionFinding> future = executor.enrich(
            task, decision, null, null, null, true);

        ExecutionFinding result = future.get();
        assertNotNull(result);
        assertEquals(TaskStatus.SUCCESS, taskStore.findById("test-task-5").get().status());
    }
}
