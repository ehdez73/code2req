package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.executor.ContextBudgetCalculator;
import com.github.ehdez73.code2req.executor.SemanticExecutor;
import com.github.ehdez73.code2req.executor.SimulationStub;
import com.github.ehdez73.code2req.executor.testmining.PairedExecutionResolver;
import com.github.ehdez73.code2req.executor.testmining.TestAssertionExtractor;
import com.github.ehdez73.code2req.executor.testmining.TestFileMatcher;
import com.github.ehdez73.code2req.model.ExecutionConfig;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.orchestrator.Phase2Orchestrator;
import com.github.ehdez73.code2req.planner.Phase2Planner;
import com.github.ehdez73.code2req.planner.QualificationRule;
import com.github.ehdez73.code2req.planner.rule.CustomConstraintValidatorRule;
import com.github.ehdez73.code2req.planner.rule.JpqlHqlQueryRule;
import com.github.ehdez73.code2req.planner.rule.NativeSqlQueryRule;
import com.github.ehdez73.code2req.planner.rule.ScheduledTaskPresentRule;
import com.github.ehdez73.code2req.planner.rule.SpringDataInterfaceRule;
import com.github.ehdez73.code2req.planner.rule.StoredProcedureCallRule;
import com.github.ehdez73.code2req.planner.rule.TestAssertionsPresentRule;
import com.github.ehdez73.code2req.planner.rule.UnresolvedFloatingLinkRule;
import com.github.ehdez73.code2req.planner.rule.UnresolvedSignaturesRule;
import com.github.ehdez73.code2req.service.FilePathResolver;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import com.github.ehdez73.code2req.synthesis.Phase3Orchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunCommandTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private TaskStore taskStore;
    private MetricsStore metricsStore;
    private RunCommand command;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("run-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        taskStore = new TaskStore(jdbc);
        var findingStore = new ExecutionFindingStore(jdbc);
        metricsStore = new MetricsStore(jdbc);
        var floatingLinkStore = new FloatingLinkStore(jdbc);
        var topicLinkStore = new TopicLinkStore(jdbc);
        var taskIdHasher = new TaskIdHasher();
        var budgetCalculator = new ContextBudgetCalculator();
        var simulationStub = new SimulationStub();
        var executionConfig = new ExecutionConfig(5, 3, 0.20, 5, 5, 500000, 0.7, List.of("Test", "IT"), null);

        var tfm = new TestFileMatcher(executionConfig);
        List<QualificationRule> rules = List.of(
            new SpringDataInterfaceRule(),
            new StoredProcedureCallRule(),
            new CustomConstraintValidatorRule(),
            new ScheduledTaskPresentRule(),
            new UnresolvedSignaturesRule(jdbc, 5),
            new UnresolvedFloatingLinkRule(floatingLinkStore),
            new TestAssertionsPresentRule(tfm),
            new NativeSqlQueryRule(),
            new JpqlHqlQueryRule()
        );

        var planner = new Phase2Planner(taskStore, jdbc, rules, findingStore);
        var txManager = new DataSourceTransactionManager(ds);
        var txTemplate = new TransactionTemplate(txManager);
        var executor = new SemanticExecutor(null, findingStore, taskStore,
            budgetCalculator, simulationStub, null, null, new TestAssertionExtractor(),
            executionConfig, null, txTemplate);
        var per = new PairedExecutionResolver(
            new TestFileMatcher(executionConfig), new TestAssertionExtractor());
        var phase2Orchestrator = new Phase2Orchestrator(planner, executor, taskStore,
            findingStore, metricsStore, executionConfig, taskIdHasher, budgetCalculator,
            new ManifestLoader(), new FilePathResolver(), per);

        var phase3Orchestrator = new Phase3Orchestrator(
            taskStore, findingStore, floatingLinkStore, topicLinkStore, metricsStore);
        var manifestLoader = new ManifestLoader();
        var manifestValidator = new ManifestValidator(manifestLoader);

        command = new RunCommand(phase2Orchestrator, phase3Orchestrator,
            manifestValidator,
            taskStore, findingStore, topicLinkStore, floatingLinkStore);
    }

    @Test
    void runWithEmptyQualifiedTasksCompletes() {
        String result = command.run("project-manifest.yaml", true, false, null);
        assertTrue(result.contains("Completed: 0"));
        assertTrue(result.contains("Phase 2"));
        assertTrue(result.contains("Phase 3"));
    }

    @Test
    void runDryRunWithQualifiedTasksCompletesWithoutApiCalls() {
        insertIndexedTask("t1", "/src/ScheduledService.java");
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t1", "SCHEDULED_TASK", "{}", 1);

        String result = command.run("project-manifest.yaml", true, false, null);
        assertTrue(result.contains("Completed: 1"));
        assertTrue(result.contains("DRY RUN"));
        assertTrue(result.contains("Phase 2"));
        assertTrue(result.contains("Phase 3"));
    }

    @Test
    void runWithLlmThresholdZeroSkipsPhase2() {
        insertIndexedTask("t1", "/src/ScheduledService.java");
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t1", "SCHEDULED_TASK", "{}", 1);

        String result = command.run("project-manifest.yaml", false, false, 0);
        assertTrue(result.contains("skipping Phase 2"));
        assertTrue(result.contains("Phase 3"));
    }

    @Test
    void runDryRunWithMultipleQualifiedTasks() {
        insertIndexedTask("t1", "/src/ServiceA.java");
        insertIndexedTask("t2", "/src/ServiceB.java");
        insertIndexedTask("t3", "/src/ServiceC.java");
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t1", "SPRING_DATA_INTERFACE", "{}", 1);
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t2", "NATIVE_SQL_QUERY", "{}", 1);
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t3", "SCHEDULED_TASK", "{}", 1);

        String result = command.run("project-manifest.yaml", true, false, null);
        assertTrue(result.contains("Completed: 3"));
        assertTrue(result.contains("Run Complete"));
    }

    @Test
    void runWithInvalidManifestReturnsError() {
        String result = command.run("nonexistent.yaml", false, false, null);
        assertTrue(result.contains("Error: Manifest file not found"));
    }

    @Test
    void resumeRecoversFailedTasksWithFindings() {
        taskStore.save(new Task("f1", "/src/FailedService.java", TaskStatus.FAILED, "java", "hash-f1", "test"));
        taskStore.save(new Task("f2", "/src/FailedService2.java", TaskStatus.FAILED, "java", "hash-f2", "test"));
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "f1", "CALL_GRAPH_EDGE", "{}", 1);
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "f2", "COMPONENT", "{}", 1);

        String result = command.run("project-manifest.yaml", true, true, null);

        assertTrue(result.contains("FAILED"));
        assertEquals(TaskStatus.ENRICHED, taskStore.findById("f1").orElseThrow().status());
        assertEquals(TaskStatus.ENRICHED, taskStore.findById("f2").orElseThrow().status());
    }

    @Test
    void resumeRecoversFailedTasksWithoutFindings() {
        taskStore.save(new Task("f3", "/src/UnreadableService.java", TaskStatus.FAILED, "java", "hash-f3", "test"));

        String result = command.run("project-manifest.yaml", true, true, null);

        assertTrue(result.contains("FAILED"));
        assertEquals(TaskStatus.INDEXED, taskStore.findById("f3").orElseThrow().status());
    }

    private void insertIndexedTask(String taskId, String filePath) {
        taskStore.save(new Task(taskId, filePath, TaskStatus.INDEXED, "java", "hash-" + taskId, "test"));
    }
}
