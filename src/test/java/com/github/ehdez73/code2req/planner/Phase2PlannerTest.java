package com.github.ehdez73.code2req.planner;

import com.github.ehdez73.code2req.model.ExecutionConfig;
import com.github.ehdez73.code2req.model.PlannerDecision;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
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
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Phase2PlannerTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private TaskStore taskStore;
    private ExecutionFindingStore findingStore;
    private FloatingLinkStore floatingLinkStore;
    private List<QualificationRule> defaultRules;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("planner-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        taskStore = new TaskStore(jdbc);
        findingStore = new ExecutionFindingStore(jdbc);
        floatingLinkStore = new FloatingLinkStore(jdbc);
        defaultRules = List.of(
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
    }

    private Phase2Planner createPlanner() {
        return new Phase2Planner(taskStore, jdbc, defaultRules, findingStore);
    }

    private void insertTask(String taskId, String filePath) {
        taskStore.save(new Task(taskId, filePath, TaskStatus.SUCCESS, "java", "hash", "test"));
    }

    private void insertFinding(String taskId, String findingType, boolean resolved) {
        jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
            taskId, findingType, "{}", resolved ? 1 : 0);
    }

    private void insertFloatingLink(String sourceFilePath, String status) {
        jdbc.update("INSERT INTO floating_links (method, url_or_path, is_expression, source_task_id, target_endpoint, confidence, resolved_status) VALUES (?, ?, ?, ?, ?, ?, ?)",
            "GET", "/api/test", 0, sourceFilePath, null, 0.0, status);
    }

    @Nested
    class PlanTests {

        @Test
        void returnsEmptyWhenNoTasks() {
            var planner = createPlanner();
            assertTrue(planner.plan().isEmpty());
        }

        @Test
        void noRulesMatch() {
            insertTask("t1", "/src/Foo.java");
            insertFinding("t1", "CALL_GRAPH_EDGE", true);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertFalse(decisions.get(0).qualified());
            assertEquals(List.of(QualificationReason.NONE), decisions.get(0).reasons());
        }

        @Test
        void unresolvedSignaturesExceeded() {
            insertTask("t1", "/src/Foo.java");
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).qualified());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.UNRESOLVED_SIGNATURES_EXCEEDED));
        }

        @Test
        void unresolvedSignaturesBelowThreshold() {
            insertTask("t1", "/src/Foo.java");
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertFalse(decisions.get(0).qualified());
        }

        @Test
        void springDataInterface() {
            insertTask("t1", "/src/FooRepo.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "SPRING_DATA_INTERFACE", "{}", 1);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.SPRING_DATA_INTERFACE));
        }

        @Test
        void storedProcedureCall() {
            insertTask("t1", "/src/FooRepo.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "DATABASE_PROCEDURE_CALL", "{}", 1);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.STORED_PROCEDURE_CALL));
        }

        @Test
        void customConstraintValidator() {
            insertTask("t1", "/src/FooValidator.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "CONSTRAINT_VALIDATOR", "{}", 1);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.CUSTOM_CONSTRAINT_VALIDATOR));
        }

        @Test
        void unresolvedFloatingLink() {
            insertTask("t1", "/src/FooClient.java");
            insertFloatingLink("/src/FooClient.java", "PENDING");
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.UNRESOLVED_FLOATING_LINK));
        }

        @Test
        void resolvedFloatingLinkDoesNotQualify() {
            insertTask("t1", "/src/FooClient.java");
            insertFloatingLink("/src/FooClient.java", "RESOLVED");
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertFalse(decisions.get(0).qualified());
        }

        @Test
        void scheduledTask() {
            insertTask("t1", "/src/Scheduler.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "SCHEDULED_TASK", "{}", 1);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.SCHEDULED_TASK_PRESENT));
        }

        @Test
        void nativeSqlQuery() {
            insertTask("t1", "/src/OrderRepo.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "NATIVE_SQL_QUERY", "{}", 1);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.NATIVE_SQL_QUERY));
        }

        @Test
        void jpqlHqlQuery() {
            insertTask("t1", "/src/OrderRepo.java");
            jdbc.update("INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved) VALUES (?, ?, ?, ?)",
                "t1", "JPQL_HQL_QUERY", "{}", 1);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            assertTrue(decisions.get(0).reasons().contains(QualificationReason.JPQL_HQL_QUERY));
        }

        @Test
        void multipleReasons() {
            insertTask("t1", "/src/Foo.java");
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "SCHEDULED_TASK", true);
            var planner = createPlanner();
            var decisions = planner.plan();
            assertEquals(1, decisions.size());
            var reasons = decisions.get(0).reasons();
            assertTrue(reasons.contains(QualificationReason.UNRESOLVED_SIGNATURES_EXCEEDED));
            assertTrue(reasons.contains(QualificationReason.SCHEDULED_TASK_PRESENT));
        }

        @Test
        void customThresholdLower() {
            var rules = List.of(
                new SpringDataInterfaceRule(),
                new StoredProcedureCallRule(),
                new CustomConstraintValidatorRule(),
                new ScheduledTaskPresentRule(),
                new UnresolvedSignaturesRule(jdbc, 2),
                new UnresolvedFloatingLinkRule(floatingLinkStore),
                new TestAssertionsPresentRule(),
                new NativeSqlQueryRule(),
                new JpqlHqlQueryRule()
            );
            insertTask("t1", "/src/Foo.java");
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            insertFinding("t1", "CALL_GRAPH_EDGE", false);
            var planner = new Phase2Planner(taskStore, jdbc, rules, findingStore);
            var decisions = planner.plan();
            assertTrue(decisions.get(0).qualified());
        }
    }

    @Nested
    class HasPairedTestFileTests {

        @Test
        void noTestFile() {
            assertFalse(TestAssertionsPresentRule.hasPairedTestFile("/src/Foo.java"));
        }

        @Test
        void sameDirTestFile() throws Exception {
            var sourceFile = tempDir.resolve("src/main/java/com/example/Foo.java");
            var testFile = tempDir.resolve("src/main/java/com/example/FooTest.java");
            Files.createDirectories(testFile.getParent());
            Files.createFile(testFile);
            assertTrue(TestAssertionsPresentRule.hasPairedTestFile(sourceFile.toString()));
        }

        @Test
        void nonJavaFile() {
            assertFalse(TestAssertionsPresentRule.hasPairedTestFile("readme.txt"));
        }

        @Test
        void integrationTestSuffix() throws Exception {
            var sourceFile = tempDir.resolve("src/main/java/com/example/Foo.java");
            var testFile = tempDir.resolve("src/main/java/com/example/FooIT.java");
            Files.createDirectories(testFile.getParent());
            Files.createFile(testFile);
            assertTrue(TestAssertionsPresentRule.hasPairedTestFile(sourceFile.toString()));
        }
    }
}
