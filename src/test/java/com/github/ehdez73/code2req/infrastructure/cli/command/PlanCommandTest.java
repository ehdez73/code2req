package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.infrastructure.config.ManifestLoader;
import com.github.ehdez73.code2req.infrastructure.config.ManifestValidator;
import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.TestFileMatcher;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionConfig;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.enrichment.domain.planner.Phase2Planner;
import com.github.ehdez73.code2req.enrichment.domain.planner.QualificationRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.CustomConstraintValidatorRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.JpqlHqlQueryRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.NativeSqlQueryRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.ScheduledTaskPresentRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.SpringDataInterfaceRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.StoredProcedureCallRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.TestAssertionsPresentRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.UnresolvedFloatingLinkRule;
import com.github.ehdez73.code2req.enrichment.domain.planner.rule.UnresolvedSignaturesRule;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanCommandTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private TaskStore taskStore;
    private PlanCommand command;
    private int initialTaskCount;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("plan-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        taskStore = new TaskStore(jdbc);
        var findingStore = new ExecutionFindingStore(jdbc);
        var floatingLinkStore = new FloatingLinkStore(jdbc);

        var tfm = new TestFileMatcher(
            new ExecutionConfig(null, null, null, null, null, null, null, null, null, null, null));
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
        var manifestLoader = new ManifestLoader();
        var manifestValidator = new ManifestValidator(manifestLoader);
        command = new PlanCommand(planner, manifestLoader, manifestValidator);

        initialTaskCount = taskStore.count();
    }

    @Test
    void planWithEmptyStoreShowsNoTasks() {
        String result = command.plan("non-existent-manifest.yaml");
        assertTrue(result.contains("Error: Manifest file not found"));
    }

    @Test
    void planWithNoQualifiedTasksShowsSuggestions() {
        insertTask("t1", "/src/Foo.java");
        insertFinding("t1", "CALL_GRAPH_EDGE", true);

        String result = command.plan("project-manifest.yaml");
        assertTrue(result.contains("No tasks qualified"));
        assertTrue(result.contains("lowering 'llm-unresolved-threshold'"));
        assertTrue(result.contains("Zero LLM calls made"));
    }

    @Test
    void planShowsQualifiedTasksWithReasons() {
        insertTask("t1", "/src/SpringDataRepo.java");
        insertFinding("t1", "SPRING_DATA_INTERFACE", true);

        String result = command.plan("project-manifest.yaml");
        assertTrue(result.contains("Qualified: 1"));
        assertTrue(result.contains("/src/SpringDataRepo.java"));
        assertTrue(result.contains("SPRING_DATA_INTERFACE"));
        assertTrue(result.contains("Zero LLM calls made"));
    }

    @Test
    void planShowsMixedQualifiedAndNonQualified() {
        insertTask("t1", "/src/ScheduledService.java");
        insertFinding("t1", "SCHEDULED_TASK", true);

        insertTask("t2", "/src/SimpleUtil.java");
        insertFinding("t2", "CALL_GRAPH_EDGE", true);

        String result = command.plan("project-manifest.yaml");
        assertTrue(result.contains("Qualified: 1"));
        assertTrue(result.contains("Not qualified: 1"));
        assertTrue(result.contains("/src/ScheduledService.java"));
        assertTrue(result.contains("/src/SimpleUtil.java"));
        assertTrue(result.contains("SCHEDULED_TASK_PRESENT"));
    }

    @Test
    void planWithMultipleReasonsShowsAll() {
        insertTask("t1", "/src/ComplexService.java");
        insertFinding("t1", "SPRING_DATA_INTERFACE", true);
        insertFinding("t1", "SCHEDULED_TASK", true);
        insertFinding("t1", "NATIVE_SQL_QUERY", true);

        String result = command.plan("project-manifest.yaml");
        assertTrue(result.contains("Qualified: 1"));
        assertTrue(result.contains("/src/ComplexService.java"));
        assertTrue(result.contains("SPRING_DATA_INTERFACE"));
        assertTrue(result.contains("SCHEDULED_TASK_PRESENT"));
        assertTrue(result.contains("NATIVE_SQL_QUERY"));
    }

    @Test
    void planTransitionsQualifiedToEnrichPending() {
        insertTask("t1", "/src/Foo.java");
        insertFinding("t1", "SPRING_DATA_INTERFACE", true);

        command.plan("project-manifest.yaml");

        Task task = taskStore.findById("t1").orElseThrow();
        assertEquals(TaskStatus.ENRICH_PENDING, task.status(),
            "qualified task should transition to ENRICH_PENDING");
    }

    @Test
    void planLeavesNonQualifiedAsIndexed() {
        insertTask("t1", "/src/Foo.java");
        insertFinding("t1", "CALL_GRAPH_EDGE", true);

        command.plan("project-manifest.yaml");

        Task task = taskStore.findById("t1").orElseThrow();
        assertEquals(TaskStatus.INDEXED, task.status(),
            "non-qualified task should remain INDEXED");
    }

    private void insertTask(String taskId, String filePath) {
        taskStore.save(new Task(taskId, filePath, TaskStatus.INDEXED, "java", "hash-" + taskId, "test"));
    }

    private void insertFinding(String taskId, String findingType, boolean resolved) {
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            taskId, findingType, "{}", resolved ? 1 : 0);
    }
}
