package com.github.ehdez73.code2req.indexing;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.ehdez73.code2req.indexing.domain.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentVisitor;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.Pass1DeclarationCollector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLinkResolver;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkResolver;
import com.github.ehdez73.code2req.indexing.domain.service.SecretRedactor;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskIdHasher;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.github.ehdez73.code2req.common.domain.ScanTarget;

import static org.junit.jupiter.api.Assertions.*;

class IndexingOrchestratorTest {

    @TempDir
    Path tempDir;

    private TaskStore taskStore;
    private TaskIdHasher taskIdHasher;
    private IndexingOrchestrator pipeline;

    @BeforeEach
    void setUp() {
        var pass1Collector = new Pass1DeclarationCollector();
        List<AstAnalysisVisitor> visitors = List.of(new ComponentVisitor());
        var astAnalyzer = new JavaAstAnalyzer(visitors);
        var secretRedactor = new SecretRedactor();
        taskIdHasher = new TaskIdHasher();

        var dbPath = tempDir.resolve("pipeline-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        taskStore = new TaskStore(jdbc);

        var executionFindingStore = new ExecutionFindingStore(jdbc);
        var topicLinkStore = new TopicLinkStore(jdbc);
        var floatingLinkStore = new FloatingLinkStore(jdbc);
        var metricsStore = new MetricsStore(jdbc);
        var txManager = new DataSourceTransactionManager(ds);
        var txTemplate = new TransactionTemplate(txManager);
        pipeline = new IndexingOrchestrator(pass1Collector, astAnalyzer, secretRedactor, taskStore, taskIdHasher,
            new TopicLinkResolver(), new FloatingLinkResolver(),
            executionFindingStore, topicLinkStore, floatingLinkStore, metricsStore, txTemplate);
    }

    @Test
    void pass1CollectsDeclarationsBeforePass2() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path a = src.resolve("A.java");
        Files.writeString(a, """
            package com.app;
            public class A { public void methodA() {} }
            """);
        Path b = src.resolve("B.java");
        Files.writeString(b, """
            package com.app;
            public class B { public void methodB(String s) {} }
            """);

        var report = new StringBuilder();
        var result = pipeline.execute(List.of(a, b), List.<ScanTarget>of(), report);

        assertTrue(result.declarationRegistry().hasClass("A"), "Pass 1 should collect class A");
        assertTrue(result.declarationRegistry().hasClass("B"), "Pass 1 should collect class B");
        assertEquals(1, result.declarationRegistry().findMethod("A", "methodA", 0).size());
        assertEquals(1, result.declarationRegistry().findMethod("B", "methodB", 1).size());

        assertTrue(result.declarationRegistry().isFrozen(), "Registry should be frozen after Pass 1");

        assertEquals(2, result.analyzedCount(), "Pass 2 should analyze both files");
        assertEquals(2, result.results().size(), "Should produce 2 analysis results");
    }

    @Test
    void emptyFileListProducesEmptyResult() {
        var report = new StringBuilder();
        var result = pipeline.execute(List.of(), List.<ScanTarget>of(), report);

        assertTrue(result.declarationRegistry().isEmpty());
        assertEquals(0, result.analyzedCount());
        assertTrue(result.results().isEmpty());
    }

    @Test
    void parseErrorInPass1DoesNotBlockOtherFiles() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path valid = src.resolve("Valid.java");
        Files.writeString(valid, """
            package com.app;
            public class Valid { public void doThing() {} }
            """);
        Path invalid = src.resolve("Invalid.java");
        Files.writeString(invalid, "this is not valid java @@");

        var report = new StringBuilder();
        var result = pipeline.execute(List.of(valid, invalid), List.<ScanTarget>of(), report);

        assertTrue(result.declarationRegistry().hasClass("Valid"), "Valid file should be collected");
        assertFalse(result.declarationRegistry().isEmpty(), "Registry should not be empty");
    }

    @Test
    void unreadableFileProducesFailedTask() {
        var report = new StringBuilder();
        var result = pipeline.execute(List.of(Path.of("/nonexistent/File.java")), List.<ScanTarget>of(), report);

        assertEquals(0, result.analyzedCount());
        assertEquals(0, result.results().size());
    }

    @Test
    void registryContainsCrossFileDeclarations() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path a = src.resolve("ServiceA.java");
        Files.writeString(a, """
            package com.app;
            public class ServiceA {
                public String greet(String name) { return "hi"; }
            }
            """);
        Path b = src.resolve("ServiceB.java");
        Files.writeString(b, """
            package com.app;
            public class ServiceB {
                public int calculate(int x, int y) { return x + y; }
            }
            """);

        var report = new StringBuilder();
        var result = pipeline.execute(List.of(a, b), List.<ScanTarget>of(), report);

        assertEquals(1, result.declarationRegistry().findMethod("ServiceA", "greet", 1).size());
        assertEquals(1, result.declarationRegistry().findMethod("ServiceB", "calculate", 2).size());
    }
}
