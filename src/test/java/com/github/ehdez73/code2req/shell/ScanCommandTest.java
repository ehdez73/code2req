package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.declaration.Pass1DeclarationCollector;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointDetector;
import com.github.ehdez73.code2req.analyzer.web.endpoint.detector.SpringEndpointDetector;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLinkResolver;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkResolver;
import com.github.ehdez73.code2req.analyzer.web.endpoint.WebXmlAnalyzer;
import com.github.ehdez73.code2req.config.ExcludeFilter;
import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.config.SecretRedactor;
import com.github.ehdez73.code2req.output.IndexWriter;
import com.github.ehdez73.code2req.output.OrphanRecovery;
import com.github.ehdez73.code2req.pipeline.ScanPipeline;
import com.github.ehdez73.code2req.analyzer.web.template.TemplateAnalyzer;
import com.github.ehdez73.code2req.analyzer.web.template.TemplateLinkResolver;
import com.github.ehdez73.code2req.shell.ScanCommand;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScanCommandTest {

    @TempDir
    Path tempDir;

    private ManifestLoader manifestLoader;
    private ManifestValidator manifestValidator;
    private ExcludeFilter excludeFilter;
    private SecretRedactor secretRedactor;
    private JavaAstAnalyzer astAnalyzer;
    private TaskStore taskStore;
    private TaskIdHasher taskIdHasher;
    private IndexWriter indexWriter;
    private OrphanRecovery orphanRecovery;
    private ScanCommand command;
    private TopicLinkResolver topicLinkResolver;

    private Path dbPath;

    @BeforeEach
    void setUp() throws IOException {
        manifestLoader = new ManifestLoader();
        manifestValidator = new ManifestValidator(manifestLoader);
        excludeFilter = new ExcludeFilter();
        secretRedactor = new SecretRedactor();
        topicLinkResolver = new TopicLinkResolver();

        // Create a real task store backed by a temp-file SQLite database
        dbPath = tempDir.resolve("test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        var schema = new com.github.ehdez73.code2req.store.TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        taskStore = new TaskStore(jdbc);
        taskIdHasher = new TaskIdHasher();

        // Set up JavaAstAnalyzer with real visitors
        var endpointDetectors = List.<EndpointDetector>of(new SpringEndpointDetector());
        var visitors = List.of(
            new com.github.ehdez73.code2req.analyzer.bean.ComponentVisitor(),
            new com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointVisitor(endpointDetectors),
            new com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskVisitor(),
            new com.github.ehdez73.code2req.analyzer.event.listener.EventListenerVisitor(),
            new com.github.ehdez73.code2req.analyzer.validator.ValidatorVisitor(),
            new com.github.ehdez73.code2req.analyzer.event.broker.kafka.KafkaVisitor(),
            new com.github.ehdez73.code2req.analyzer.bean.java.BeanMethodVisitor(),
            new com.github.ehdez73.code2req.analyzer.event.broker.rabbitmq.RabbitMqVisitor(),
            new com.github.ehdez73.code2req.analyzer.event.broker.activemq.ActiveMqVisitor()
        );
        astAnalyzer = new JavaAstAnalyzer(visitors);

        indexWriter = new IndexWriter(new com.github.ehdez73.code2req.model.OutputConfig(tempDir.toString(), "code-graph-index.json", null));
        orphanRecovery = new OrphanRecovery(taskStore);

        var pass1Collector = new Pass1DeclarationCollector();
        var executionFindingStore = new ExecutionFindingStore(jdbc);
        var topicLinkStore = new com.github.ehdez73.code2req.store.TopicLinkStore(jdbc);
        var floatingLinkStore = new com.github.ehdez73.code2req.store.FloatingLinkStore(jdbc);
        var metricsStore = new com.github.ehdez73.code2req.store.MetricsStore(jdbc);
        var pipeline = new ScanPipeline(pass1Collector, astAnalyzer, secretRedactor, taskStore, taskIdHasher,
            topicLinkResolver, new FloatingLinkResolver(),
            executionFindingStore, topicLinkStore, floatingLinkStore, metricsStore);

        var templateAnalyzer = new TemplateAnalyzer(List.of(new com.github.ehdez73.code2req.analyzer.web.template.JspTemplateParser(), new com.github.ehdez73.code2req.analyzer.web.template.ThymeleafTemplateParser()));
        var templateLinkResolver = new TemplateLinkResolver();
        var webXmlAnalyzer = new WebXmlAnalyzer();

        command = new ScanCommand(
            manifestLoader, manifestValidator, excludeFilter, pipeline,
            taskStore, taskIdHasher, indexWriter, orphanRecovery,
            templateAnalyzer, templateLinkResolver, executionFindingStore,
            webXmlAnalyzer);
    }

    @Test
    void scanWithValidManifestAndJavaFiles() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path javaFile = src.resolve("App.java");
        Files.writeString(javaFile, """
            package com.app;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.GetMapping;
            @RestController
            public class App {
                @GetMapping("/api/hello")
                public String hello() { return "hi"; }
            }
            """);

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: test-app
                path: %s
            """.formatted(src.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.scan(manifest.toString(), false);

        assertTrue(result.contains("Scan Complete"), "Expected scan completion message");
        assertTrue(result.contains("Phase 1/5 — Manifest: OK"), "Expected manifest phase OK");
        assertTrue(result.contains("Phase 3/5 — File Discovery"), "Expected file discovery phase");
        assertTrue(result.contains("Phase 4/5 — Analysis"), "Expected analysis phase");
        assertTrue(result.contains("Phase 5/5 — Index Output"), "Expected index output phase");

        assertTrue(taskStore.count() > 0, "Expected tasks in store");
        assertTrue(Files.exists(tempDir.resolve("code-graph-index.json")), "Expected index file");
    }

    @Test
    void scanWithManifestNotFound() {
        String result = command.scan(tempDir.resolve("nonexistent.yaml").toString(), false);
        assertTrue(result.contains("Error: Manifest file not found"));
    }

    @Test
    void scanWithValidationErrors() throws IOException {
        Path manifest = tempDir.resolve("bad-manifest.yaml");
        Files.writeString(manifest, "targets: []");

        String result = command.scan(manifest.toString(), false);
        assertTrue(result.contains("FAILED"));
        assertTrue(result.contains("at least one target"));
    }

    @Test
    void scanWithNoJavaFiles() throws IOException {
        Path targetDir = Files.createDirectories(tempDir.resolve("empty-target"));
        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: empty
                path: %s
            """.formatted(targetDir.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.scan(manifest.toString(), false);
        assertTrue(result.contains("no Java files found"));
    }

    @Test
    void scanAppliesExcludeFilter() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("proj/src"));
        Path included = src.resolve("App.java");
        Files.writeString(included, """
            package com.app;
            public class App { public void run() {} }
            """);
        Path excluded = src.resolve("Generated.java");
        Files.writeString(excluded, """
            package com.app;
            public class Generated { public void run() {} }
            """);

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: test-app
                path: %s
                exclude_patterns:
                  - "**/Generated.java"
            """.formatted(src.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.scan(manifest.toString(), false);
        assertTrue(result.contains("Scan Complete"));
        assertTrue(taskStore.count() > 0, "Expected tasks in store");
    }

    @Test
    void scanWithMultipleTargets() throws IOException {
        Path modA = Files.createDirectories(tempDir.resolve("mod-a"));
        Files.writeString(modA.resolve("A.java"), """
            package com.a;
            import org.springframework.stereotype.Service;
            @Service
            public class AService { public void serve() {} }
            """);
        Path modB = Files.createDirectories(tempDir.resolve("mod-b"));
        Files.writeString(modB.resolve("B.java"), """
            package com.b;
            import org.springframework.stereotype.Service;
            @Service
            public class BService { public void serve() {} }
            """);

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: mod-a
                path: %s
              - name: mod-b
                path: %s
            """.formatted(modA.toAbsolutePath().toString().replace("\\", "\\\\"),
                           modB.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.scan(manifest.toString(), false);
        assertTrue(result.contains("Scan Complete"));
        assertTrue(result.contains("2 target(s)"));
    }

    @Test
    void scanWithRedactedContent() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("secure-src"));
        Path javaFile = src.resolve("Config.java");
        Files.writeString(javaFile, """
            package com.app;
            public class Config {
                public String getPassword() { return "super_secret_123"; }
            }
            """);

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: secure-app
                path: %s
            """.formatted(src.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.scan(manifest.toString(), false);
        assertTrue(result.contains("Scan Complete"));
    }
}
