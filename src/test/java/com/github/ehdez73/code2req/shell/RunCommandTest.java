package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.executor.ContextBudgetCalculator;
import com.github.ehdez73.code2req.executor.SemanticExecutor;
import com.github.ehdez73.code2req.executor.SimulationStub;
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
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import com.github.ehdez73.code2req.synthesis.Phase3Orchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

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
        var taskIdHasher = new TaskIdHasher();
        var budgetCalculator = new ContextBudgetCalculator();
        var simulationStub = new SimulationStub();
        var executionConfig = new ExecutionConfig(5, 3, 0.20, 5, 5, 500000, 0.7);

        List<QualificationRule> rules = List.of(
            new SpringDataInterfaceRule(),
            new StoredProcedureCallRule(),
            new CustomConstraintValidatorRule(),
            new ScheduledTaskPresentRule(),
            new UnresolvedSignaturesRule(jdbc, 5),
            new UnresolvedFloatingLinkRule(floatingLinkStore),
            new TestAssertionsPresentRule(),
            new NativeSqlQueryRule(),
            new JpqlHqlQueryRule()
        );

        var planner = new Phase2Planner(taskStore, jdbc, rules, findingStore);
        var executor = new SemanticExecutor(null, findingStore, taskStore,
            budgetCalculator, simulationStub, null, null);
        var phase2Orchestrator = new Phase2Orchestrator(planner, executor, taskStore,
            findingStore, metricsStore, executionConfig, taskIdHasher, budgetCalculator);

        var phase3Orchestrator = new Phase3Orchestrator(metricsStore);
        var manifestLoader = new ManifestLoader();
        var manifestValidator = new ManifestValidator(manifestLoader);

        command = new RunCommand(phase2Orchestrator, phase3Orchestrator,
            manifestLoader, manifestValidator);
    }

    @Test
    void runWithEmptyQualifiedTasksCompletes() {
        String result = command.run("project-manifest.yaml", true, null);
        assertTrue(result.contains("Completed: 0"));
        assertTrue(result.contains("Phase 2"));
        assertTrue(result.contains("Phase 3"));
    }

    @Test
    void runDryRunWithQualifiedTasksCompletesWithoutApiCalls() {
        insertSuccessTask("t1", "/src/ScheduledService.java");
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t1", "SCHEDULED_TASK", "{}", 1);

        String result = command.run("project-manifest.yaml", true, null);
        assertTrue(result.contains("Completed: 1"));
        assertTrue(result.contains("DRY RUN"));
        assertTrue(result.contains("Phase 2"));
        assertTrue(result.contains("Phase 3"));
    }

    @Test
    void runWithLlmThresholdZeroSkipsPhase2() {
        insertSuccessTask("t1", "/src/ScheduledService.java");
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t1", "SCHEDULED_TASK", "{}", 1);

        String result = command.run("project-manifest.yaml", false, 0);
        assertTrue(result.contains("skipping Phase 2"));
        assertTrue(result.contains("Phase 3"));
    }

    @Test
    void runDryRunWithMultipleQualifiedTasks() {
        insertSuccessTask("t1", "/src/ServiceA.java");
        insertSuccessTask("t2", "/src/ServiceB.java");
        insertSuccessTask("t3", "/src/ServiceC.java");
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t1", "SPRING_DATA_INTERFACE", "{}", 1);
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t2", "NATIVE_SQL_QUERY", "{}", 1);
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            "t3", "SCHEDULED_TASK", "{}", 1);

        String result = command.run("project-manifest.yaml", true, null);
        assertTrue(result.contains("Completed: 3"));
        assertTrue(result.contains("Run Complete"));
    }

    @Test
    void runWithInvalidManifestReturnsError() {
        String result = command.run("nonexistent.yaml", false, null);
        assertTrue(result.contains("Error: Manifest file not found"));
    }

    private void insertSuccessTask(String taskId, String filePath) {
        taskStore.save(new Task(taskId, filePath, TaskStatus.SUCCESS, "java", "hash-" + taskId));
    }
}
