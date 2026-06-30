package com.github.ehdez73.code2req.enrichment;

import com.github.ehdez73.code2req.infrastructure.config.ManifestLoader;
import com.github.ehdez73.code2req.enrichment.adapter.llm.ContextBudgetCalculator;
import com.github.ehdez73.code2req.enrichment.adapter.llm.LlmEnrichmentService;
import com.github.ehdez73.code2req.enrichment.adapter.llm.SimulationStub;
import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.PairedExecutionResolver;
import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.TestAssertionExtractor;
import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.TestFileMatcher;
import com.github.ehdez73.code2req.enrichment.domain.model.CompletionStatus;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionConfig;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.enrichment.domain.service.BranchState;
import com.github.ehdez73.code2req.enrichment.domain.service.EnrichmentDag;
import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.enrichment.domain.model.PlannerDecision;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.enrichment.domain.planner.EnrichmentPlanner;
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
import com.github.ehdez73.code2req.infrastructure.file.FilePathResolver;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskIdHasher;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentOrchestratorTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private TaskStore taskStore;
    private ExecutionFindingStore findingStore;
    private MetricsStore metricsStore;
    private FloatingLinkStore floatingLinkStore;
    private EnrichmentPlanner planner;
    private ExecutionConfig executionConfig;
    private TaskIdHasher taskIdHasher;
    private ContextBudgetCalculator budgetCalculator;
    private SimulationStub simulationStub;
    private List<QualificationRule> defaultRules;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("orchestrator-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        taskStore = new TaskStore(jdbc);
        findingStore = new ExecutionFindingStore(jdbc);
        metricsStore = new MetricsStore(jdbc);
        floatingLinkStore = new FloatingLinkStore(jdbc);
        taskIdHasher = new TaskIdHasher();
        budgetCalculator = new ContextBudgetCalculator();
        simulationStub = new SimulationStub();
        executionConfig = new ExecutionConfig(5, 3, 0.20, 5, 5, 500000, 0.7, List.of("Test", "IT"), null, null, null);

        var tfm = new TestFileMatcher(executionConfig);
        defaultRules = List.of(
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
    }

    private EnrichmentOrchestrator createOrchestrator() {
        planner = new EnrichmentPlanner(taskStore, jdbc, defaultRules, findingStore);
        var txManager = new DataSourceTransactionManager(jdbc.getDataSource());
        var txTemplate = new TransactionTemplate(txManager);
        var executor = new LlmEnrichmentService(null, findingStore, taskStore,
            budgetCalculator, simulationStub, null, null, new TestAssertionExtractor(),
            executionConfig, null, txTemplate);
        var manifestLoader = new ManifestLoader();
        var filePathResolver = new FilePathResolver();
        var per = new PairedExecutionResolver(
            new TestFileMatcher(executionConfig), new TestAssertionExtractor());
        return new EnrichmentOrchestrator(planner, executor, taskStore, findingStore,
            metricsStore, executionConfig, taskIdHasher, budgetCalculator,
            manifestLoader, filePathResolver, per);
    }

    private void insertTask(String taskId, String filePath) {
        taskStore.save(new Task(taskId, filePath, TaskStatus.INDEXED, "java", "hash-" + taskId, "test"));
    }

    private Path createTestFile(String relativePath) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "public class " + file.getFileName().toString().replace(".java", "") + " {}");
        return file;
    }

    private Path createTestManifest(Path targetRoot) throws IOException {
        Path manifest = tempDir.resolve("test-manifest.yaml");
        Files.writeString(manifest, "targets:\n  - name: test-target\n    path: " + targetRoot.toAbsolutePath().normalize() + "\n    layer: test\n    tech_profile: java\n");
        return manifest;
    }

    @Nested
    class OrchestratorExecutionTests {

        @Test
        void returnsEmptyWhenNoQualifiedTasks() {
            insertTask("t1", "/src/Foo.java");
            var orchestrator = createOrchestrator();
            CompletionStatus status = orchestrator.execute("project-manifest.yaml", true);
            assertEquals(0, status.tasksSubmitted());
            assertEquals(0, status.tasksCompleted());
            assertTrue(status.allSucceeded());
        }

        @Test
        void submitsAllQualifiedTasks() {
            insertTask("t1", "/src/Foo.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "SCHEDULED_TASK", "{}", 1);

            var orchestrator = createOrchestrator();
            CompletionStatus status = orchestrator.execute("project-manifest.yaml", true);

            assertEquals(1, status.tasksSubmitted());
            assertEquals(1, status.tasksCompleted());
            assertEquals(0, status.tasksFailed());
            assertEquals(TaskStatus.ENRICHED, taskStore.findById("t1").get().status());
        }

        @Test
        void barrierBlocksUntilAllTasksComplete() {
            insertTask("t1", "/src/Foo.java");
            insertTask("t2", "/src/Bar.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "SCHEDULED_TASK", "{}", 1);
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t2", "SPRING_DATA_INTERFACE", "{}", 1);

            var orchestrator = createOrchestrator();
            CompletionStatus status = orchestrator.execute("project-manifest.yaml", true);

            assertEquals(2, status.tasksCompleted());
            assertEquals(TaskStatus.ENRICHED, taskStore.findById("t1").get().status());
            assertEquals(TaskStatus.ENRICHED, taskStore.findById("t2").get().status());
        }

        @Test
        void respectsMaxHopDepth() throws IOException {
            Path rootFile = createTestFile("src/Root.java");
            createTestFile("src/Dep1.java");
            createTestFile("src/Dep2.java");
            createTestFile("src/Dep3.java");
            createTestFile("src/Dep4.java");
            Path manifestPath = createTestManifest(tempDir);

            insertTask("root", rootFile.toAbsolutePath().normalize().toString());
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "root", "SCHEDULED_TASK", "{}", 1);

            String rootPath = rootFile.toAbsolutePath().normalize().toString();
            var depPaths = Map.of(
                rootPath, List.of(
                    new ExecutionFinding.DiscoveredDependency(
                        tempDir.resolve("src/Dep1.java").toAbsolutePath().normalize().toString(), "discovered", 1),
                    new ExecutionFinding.DiscoveredDependency(
                        tempDir.resolve("src/Dep2.java").toAbsolutePath().normalize().toString(), "discovered", 2),
                    new ExecutionFinding.DiscoveredDependency(
                        tempDir.resolve("src/Dep3.java").toAbsolutePath().normalize().toString(), "discovered", 3),
                    new ExecutionFinding.DiscoveredDependency(
                        tempDir.resolve("src/Dep4.java").toAbsolutePath().normalize().toString(), "discovered", 4)
                )
            );

            var controlledStub = new ControlledSimulationStub(depPaths);
            planner = new EnrichmentPlanner(taskStore, jdbc, defaultRules, findingStore);
            var txManager = new DataSourceTransactionManager(jdbc.getDataSource());
            var txTemplate = new TransactionTemplate(txManager);
            var executor = new LlmEnrichmentService(null, findingStore, taskStore,
                budgetCalculator, controlledStub, null, null, new TestAssertionExtractor(),
                executionConfig, null, txTemplate);
            var per = new PairedExecutionResolver(
                new TestFileMatcher(executionConfig), new TestAssertionExtractor());
            var orchestratorWithDeps = new EnrichmentOrchestrator(planner, executor, taskStore,
                findingStore, metricsStore,
                new ExecutionConfig(5, 3, 0.20, 5, 5, 500000, 0.7, List.of("Test", "IT"), null, null, null),
                taskIdHasher, budgetCalculator, new ManifestLoader(),
                new FilePathResolver(), per);

            CompletionStatus status = orchestratorWithDeps.execute(manifestPath.toString(), true);

            assertEquals(TaskStatus.ENRICHED, taskStore.findById("root").get().status());
        }

        @Test
        void handlesDynamicReplanning() throws IOException {
            Path rootFile = createTestFile("src/Root.java");
            createTestFile("src/DiscoveredService.java");
            Path manifestPath = createTestManifest(tempDir);

            insertTask("root", rootFile.toAbsolutePath().normalize().toString());
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "root", "SCHEDULED_TASK", "{}", 1);

            var depPaths = Map.of(
                rootFile.toAbsolutePath().normalize().toString(), List.of(
                    new ExecutionFinding.DiscoveredDependency(
                        tempDir.resolve("src/DiscoveredService.java").toAbsolutePath().normalize().toString(),
                        "runtime reflection", 1)
                )
            );

            var controlledStub = new ControlledSimulationStub(depPaths);
            planner = new EnrichmentPlanner(taskStore, jdbc, defaultRules, findingStore);
            var txManager = new DataSourceTransactionManager(jdbc.getDataSource());
            var txTemplate = new TransactionTemplate(txManager);
            var executor = new LlmEnrichmentService(null, findingStore, taskStore,
                budgetCalculator, controlledStub, null, null, new TestAssertionExtractor(),
                executionConfig, null, txTemplate);
            var per = new PairedExecutionResolver(
                new TestFileMatcher(executionConfig), new TestAssertionExtractor());
            var orchestratorWithDeps = new EnrichmentOrchestrator(planner, executor, taskStore,
                findingStore, metricsStore, executionConfig, taskIdHasher, budgetCalculator,
                new ManifestLoader(), new FilePathResolver(), per);

            CompletionStatus status = orchestratorWithDeps.execute(manifestPath.toString(), true);

            assertEquals(2, status.tasksCompleted());
            assertEquals(1, status.dependenciesDiscovered());
            assertEquals(TaskStatus.ENRICHED, taskStore.findById("root").get().status());
        }

        @Test
        void preventsRedundantEvaluationViaVisitedRegistry() {
            insertTask("t1", "/src/Foo.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "SCHEDULED_TASK", "{}", 1);

            var orchestrator = createOrchestrator();
            var dag = new EnrichmentDag(3);
            var decision = PlannerDecision.qualified("t1", "/src/Foo.java", "test", List.of());
            dag.registerRootTask(decision);
            dag.markVisited("t1|/src/Foo.java");
            assertTrue(dag.isVisited("t1|/src/Foo.java"));
        }

        @Test
        void writesPhase2MetricsAfterCompletion() {
            insertTask("t1", "/src/Foo.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "SCHEDULED_TASK", "{}", 1);

            var orchestrator = createOrchestrator();
            orchestrator.execute("project-manifest.yaml", true);

            Metric metric = metricsStore.getLatestForPhase(2);
            assertNotNull(metric);
            assertEquals(2, metric.phase());
            assertEquals(1, metric.tasksCompleted());
            assertTrue(metric.tokensConsumed() > 0);
            assertTrue(metric.apiCostEstimated() > 0);
        }

        @Test
        void handlesMultipleQualifiedTasks() {
            insertTask("t1", "/src/Foo.java");
            insertTask("t2", "/src/Bar.java");
            insertTask("t3", "/src/Baz.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "SCHEDULED_TASK", "{}", 1);
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t2", "SPRING_DATA_INTERFACE", "{}", 1);
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t3", "NATIVE_SQL_QUERY", "{}", 1);

            var orchestrator = createOrchestrator();
            CompletionStatus status = orchestrator.execute("project-manifest.yaml", true);

            assertEquals(3, status.tasksSubmitted());
            assertEquals(3, status.tasksCompleted());
            assertEquals(0, status.tasksFailed());
        }
    }

    @Nested
    class EnrichmentDagTests {

        @Test
        void registerRootTaskCreatesBranch() {
            var dag = new EnrichmentDag(3);
            var decision = PlannerDecision.qualified("root", "/src/Root.java", "test", List.of());
            dag.registerRootTask(decision);
            assertNotNull(dag.getBranchForRoot("root"));
            assertEquals(1, dag.branchCount());
        }

        @Test
        void visitedRegistryTracksHashes() {
            var dag = new EnrichmentDag(3);
            assertFalse(dag.isVisited("hash1"));
            dag.markVisited("hash1");
            assertTrue(dag.isVisited("hash1"));
        }

        @Test
        void drainPendingReturnsAllQueuedDecisions() {
            var dag = new EnrichmentDag(3);
            var d1 = PlannerDecision.qualified("t1", "/src/Foo.java", "test", List.of());
            var d2 = PlannerDecision.qualified("t2", "/src/Bar.java", "test", List.of());
            dag.registerRootTask(d1);
            dag.registerRootTask(d2);

            List<PlannerDecision> pending = dag.drainPending();
            assertEquals(2, pending.size());
            assertTrue(dag.pendingCount() == 0);
        }

        @Test
        void allBranchesCompleteWhenNoPendingDependencies() {
            var dag = new EnrichmentDag(3);
            var d1 = PlannerDecision.qualified("t1", "/src/Foo.java", "test", List.of());
            dag.registerRootTask(d1);
            dag.drainPending();

            assertTrue(dag.allBranchesComplete());
        }
    }

    @Nested
    class BranchStateTests {

        @Test
        void initialDepthIsZero() {
            var state = new BranchState("root", 3);
            assertEquals(0, state.currentDepth());
        }

        @Test
        void incrementDepthExceedsMax() {
            var state = new BranchState("root", 3);
            state.incrementDepth();
            state.incrementDepth();
            state.incrementDepth();
            assertTrue(state.incrementDepth());
        }

        @Test
        void pauseAndResume() {
            var state = new BranchState("root", 3);
            assertFalse(state.isPaused());
            state.pause();
            assertTrue(state.isPaused());
            state.resume();
            assertFalse(state.isPaused());
        }

        @Test
        void isCompleteWhenNotPausedAndNoPending() {
            var state = new BranchState("root", 3);
            assertTrue(state.isComplete());
            state.pause();
            assertFalse(state.isComplete());
            state.resume();
            assertTrue(state.isComplete());
        }

        @Test
        void pendingDependenciesPreventCompletion() {
            var state = new BranchState("root", 3);
            state.addPendingDependency();
            assertFalse(state.isComplete());
            state.removePendingDependency();
            assertTrue(state.isComplete());
        }
    }

    static class ControlledSimulationStub extends SimulationStub {
        private final Map<String, List<ExecutionFinding.DiscoveredDependency>> depMap;
        private static final ObjectMapper MAPPER = new ObjectMapper();

        ControlledSimulationStub(Map<String, List<ExecutionFinding.DiscoveredDependency>> depMap) {
            this.depMap = depMap;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String generateEnrichment(String taskId, String filePath, String targetName) {
            String base = super.generateEnrichment(taskId, filePath, targetName);
            List<ExecutionFinding.DiscoveredDependency> extraDeps = depMap.get(filePath);

            if (extraDeps == null || extraDeps.isEmpty()) {
                return base;
            }

            try {
                var root = MAPPER.readValue(base, Map.class);
                List<Map<String, Object>> deps = new ArrayList<>();
                for (var dep : extraDeps) {
                    deps.add(Map.of(
                        "file_path", dep.filePath(),
                        "reason", dep.reason(),
                        "discovery_depth", dep.discoveryDepth()
                    ));
                }
                root.put("discovered_dependencies", deps);
                return MAPPER.writeValueAsString(root);
            } catch (Exception e) {
                throw new RuntimeException("Failed to build controlled stub JSON", e);
            }
        }
    }
}
