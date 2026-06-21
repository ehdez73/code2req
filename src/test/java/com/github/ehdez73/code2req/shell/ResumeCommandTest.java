package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.analyzer.bean.java.BeanMethodVisitor;
import com.github.ehdez73.code2req.analyzer.bean.ComponentVisitor;
import com.github.ehdez73.code2req.analyzer.declaration.Pass1DeclarationCollector;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointDetector;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointVisitor;
import com.github.ehdez73.code2req.analyzer.web.endpoint.detector.SpringEndpointDetector;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLinkResolver;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkResolver;
import com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskVisitor;
import com.github.ehdez73.code2req.analyzer.event.listener.EventListenerVisitor;
import com.github.ehdez73.code2req.analyzer.validator.ValidatorVisitor;
import com.github.ehdez73.code2req.analyzer.event.broker.kafka.KafkaVisitor;
import com.github.ehdez73.code2req.analyzer.event.broker.rabbitmq.RabbitMqVisitor;
import com.github.ehdez73.code2req.analyzer.event.broker.activemq.ActiveMqVisitor;
import com.github.ehdez73.code2req.config.ExcludeFilter;
import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.config.SecretRedactor;
import com.github.ehdez73.code2req.model.OutputConfig;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.output.IndexWriter;
import com.github.ehdez73.code2req.output.OrphanRecovery;
import com.github.ehdez73.code2req.pipeline.ScanPipeline;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeCommandTest {

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
    private ResumeCommand command;
    private TopicLinkResolver topicLinkResolver;

    private Path dbPath;

    @BeforeEach
    void setUp() throws IOException {
        manifestLoader = new ManifestLoader();
        manifestValidator = new ManifestValidator(manifestLoader);
        excludeFilter = new ExcludeFilter();
        secretRedactor = new SecretRedactor();
        topicLinkResolver = new TopicLinkResolver();
        dbPath = tempDir.resolve("resume-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();
        taskStore = new TaskStore(jdbc);
        taskIdHasher = new TaskIdHasher();

        var endpointDetectors = List.<EndpointDetector>of(new SpringEndpointDetector());
        var visitors = List.of(
            new ComponentVisitor(),
            new EndpointVisitor(endpointDetectors),
            new ScheduledTaskVisitor(),
            new EventListenerVisitor(),
            new ValidatorVisitor(),
            new KafkaVisitor(),
            new BeanMethodVisitor(),
            new RabbitMqVisitor(),
            new ActiveMqVisitor()
        );
        astAnalyzer = new JavaAstAnalyzer(visitors);

        indexWriter = new IndexWriter(new OutputConfig(tempDir.toString(), "code-graph-index.json", null));
        orphanRecovery = new OrphanRecovery(taskStore);

        var pass1Collector = new Pass1DeclarationCollector();
        var executionFindingStore = new ExecutionFindingStore(jdbc);
        var topicLinkStore = new TopicLinkStore(jdbc);
        var floatingLinkStore = new FloatingLinkStore(jdbc);
        var metricsStore = new MetricsStore(jdbc);
        var pipeline = new ScanPipeline(pass1Collector, astAnalyzer, secretRedactor, taskStore, taskIdHasher,
            topicLinkResolver, new FloatingLinkResolver(),
            executionFindingStore, topicLinkStore, floatingLinkStore, metricsStore);

        command = new ResumeCommand(
            manifestLoader, manifestValidator, excludeFilter, pipeline,
            taskStore, taskIdHasher, indexWriter, orphanRecovery);
    }

    @Test
    void resumeSkipsAlreadyCompletedFiles() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path fileA = src.resolve("A.java");
        Files.writeString(fileA, """
            package com.app;
            public class A { public void run() {} }
            """);
        Path fileB = src.resolve("B.java");
        Files.writeString(fileB, """
            package com.app;
            public class B { public void run() {} }
            """);

        String hashA = sha256Hex(Files.readString(fileA, StandardCharsets.UTF_8));
        String taskIdA = taskIdHasher.hash(fileA.toString(), hashA, "test-app");
        taskStore.save(new Task(taskIdA, fileA.toString(), TaskStatus.SUCCESS, "java", hashA, "test"));

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: test-app
                path: %s
            """.formatted(src.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.resume(manifest.toString());

        assertTrue(result.contains("Resume Complete"), "Expected resume completion message");
        assertTrue(result.contains("Skipped (already SUCCESS): 1"), "Expected A to be skipped");
        assertTrue(result.contains("Remaining: 1"), "Expected B to remain");

        assertTrue(taskStore.findById(taskIdA).isPresent(), "A should still be in store");
        assertTrue(taskStore.count() >= 2, "Expected at least 2 tasks total after resume");
    }

    @Test
    void resumeWithAllCompleted() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path fileA = src.resolve("A.java");
        Files.writeString(fileA, """
            package com.app;
            public class A { public void run() {} }
            """);

        String hashA = sha256Hex(Files.readString(fileA, StandardCharsets.UTF_8));
        String taskIdA = taskIdHasher.hash(fileA.toString(), hashA, "test-app");
        taskStore.save(new Task(taskIdA, fileA.toString(), TaskStatus.SUCCESS, "java", hashA, "test"));

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: test-app
                path: %s
            """.formatted(src.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.resume(manifest.toString());

        assertTrue(result.contains("Resume Complete"));
        assertTrue(result.contains("Skipped (already SUCCESS): 1"));
        assertTrue(result.contains("Remaining: 0"));
    }

    @Test
    void resumeRecoversOrphans() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path fileA = src.resolve("A.java");
        Files.writeString(fileA, """
            package com.app;
            public class A { public void run() {} }
            """);

        taskStore.save(new Task("orphan-1", fileA.toString(), TaskStatus.RUNNING, "java", "h1", "test"));

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: test-app
                path: %s
            """.formatted(src.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.resume(manifest.toString());

        assertTrue(result.contains("Resume Complete"));
        assertTrue(result.contains("Orphan Recovery"));
    }

    @Test
    void resumeWithEmptyStore() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path fileA = src.resolve("A.java");
        Files.writeString(fileA, """
            package com.app;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class A { public void serve() {} }
            """);

        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
            targets:
              - name: test-app
                path: %s
            """.formatted(src.toAbsolutePath().toString().replace("\\", "\\\\")));

        String result = command.resume(manifest.toString());

        assertTrue(result.contains("Resume Complete"));
        assertTrue(result.contains("Remaining: 1"));
        assertTrue(result.contains("Skipped (already SUCCESS): 0"));
        assertTrue(taskStore.count() > 0, "Expected tasks in store after fresh resume");
    }

    private static String sha256Hex(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
