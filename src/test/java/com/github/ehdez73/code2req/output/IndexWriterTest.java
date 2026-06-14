package com.github.ehdez73.code2req.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.analyzer.component.BeanMethodInfo;
import com.github.ehdez73.code2req.analyzer.component.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.analyzer.eventlistener.EventListenerInfo;
import com.github.ehdez73.code2req.analyzer.eventlistener.MethodCallInfo;
import com.github.ehdez73.code2req.analyzer.kafka.KafkaInfo;
import com.github.ehdez73.code2req.analyzer.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.analyzer.validator.ValidatorInfo;
import com.github.ehdez73.code2req.model.OutputConfig;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class IndexWriterTest {

    private final IndexWriter writer = new IndexWriter();
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void writesEmptyIndex() throws IOException {
        Path targetDir = Files.createDirectory(tempDir.resolve("src"));
        ScanTarget target = new ScanTarget("test-app", targetDir.toString(), "backend", "java-spring", List.of(), List.of());
        ProjectManifest manifest = new ProjectManifest(List.of(target), null, new OutputConfig(tempDir.toString(), "index.json", null));
        List<AnalysisResult> results = List.of();

        Path outputPath = writer.write(manifest, results);

        assertTrue(Files.exists(outputPath));
        assertEquals("index.json", outputPath.getFileName().toString());

        JsonNode root = mapper.readTree(outputPath.toFile());
        assertEquals("1.0", root.get("version").asText());
        assertTrue(root.has("generated_at"));
        assertTrue(root.has("targets"));
        assertEquals(1, root.get("targets").size());
        assertEquals("test-app", root.get("targets").get(0).get("name").asText());
    }

    @Test
    void writesMixedFindings() throws IOException {
        Path targetDir = Files.createDirectory(tempDir.resolve("proj"));
        String filePath = targetDir.resolve("App.java").toString();
        ScanTarget target = new ScanTarget("app", targetDir.toString(), "backend", "java-spring", List.of(), List.of());
        ProjectManifest manifest = new ProjectManifest(List.of(target), null, new OutputConfig(tempDir.toString(), "out.json", null));

        List<AnalysisFinding> findings = List.of(
            new ComponentInfo("RestController", "UserController", "com.app", filePath),
            new EndpointInfo("GET", "/api/users", "UserController", List.of(), List.of(), filePath),
            new ScheduledTaskInfo("cleanup", "CleanupTask", "0 0 * * *", null, null, "cron", filePath),
            new EventListenerInfo("UserCreatedEvent", "onUserCreated", "UserEventListener", filePath, List.of(
                new MethodCallInfo("EmailService", "sendWelcomeEmail", 1)
            )),
            new ValidatorInfo("EmailValidator", filePath, "Constraint", "email", "return value != null && value.contains(\"@\");", false),
            new KafkaInfo("orders", "handleOrder", "OrderListener", filePath, false),
            new BeanMethodInfo("dataSource", "javax.sql.DataSource", "AppConfig", filePath),
            new RabbitMqInfo("order.queue", "handleOrder", "OrderMqListener", filePath),
            new ActiveMqInfo("order.queue", "handleJmsOrder", "OrderJmsListener", filePath)
        );
        AnalysisResult result = new AnalysisResult(filePath, findings);

        Path outputPath = writer.write(manifest, List.of(result));
        JsonNode root = mapper.readTree(outputPath.toFile());
        JsonNode targetNode = root.get("targets").get(0);

        assertEquals("app", targetNode.get("name").asText());

        assertEquals(1, targetNode.get("components").size());
        assertEquals("UserController", targetNode.get("components").get(0).get("className").asText());
        assertEquals("RestController", targetNode.get("components").get(0).get("annotationType").asText());

        assertEquals(1, targetNode.get("endpoints").size());
        assertEquals("GET", targetNode.get("endpoints").get(0).get("httpMethod").asText());
        assertEquals("/api/users", targetNode.get("endpoints").get(0).get("path").asText());

        assertEquals(1, targetNode.get("scheduled_tasks").size());
        assertEquals("cleanup", targetNode.get("scheduled_tasks").get(0).get("methodName").asText());

        assertEquals(1, targetNode.get("event_listeners").size());
        assertEquals("UserCreatedEvent", targetNode.get("event_listeners").get(0).get("eventType").asText());

        assertEquals(1, targetNode.get("validators").size());
        assertEquals("EmailValidator", targetNode.get("validators").get(0).get("className").asText());

        assertEquals(1, targetNode.get("kafka_listeners").size());
        assertEquals("orders", targetNode.get("kafka_listeners").get(0).get("topics").asText());

        assertEquals(1, targetNode.get("bean_methods").size());
        assertEquals("dataSource", targetNode.get("bean_methods").get(0).get("beanName").asText());

        assertEquals(1, targetNode.get("rabbitmq_listeners").size());
        assertEquals("order.queue", targetNode.get("rabbitmq_listeners").get(0).get("queues").asText());

        assertEquals(1, targetNode.get("activemq_listeners").size());
        assertEquals("order.queue", targetNode.get("activemq_listeners").get(0).get("destination").asText());
    }

    @Test
    void customOutputPath() throws IOException {
        Path customDir = Files.createDirectory(tempDir.resolve("custom-out"));
        Path targetDir = Files.createDirectory(tempDir.resolve("src"));
        ScanTarget target = new ScanTarget("app", targetDir.toString(), "backend", "java", List.of(), List.of());
        ProjectManifest manifest = new ProjectManifest(List.of(target), null,
            new OutputConfig(customDir.toString(), "my-index.json", null));

        Path outputPath = writer.write(manifest, List.of());

        assertEquals("my-index.json", outputPath.getFileName().toString());
        assertEquals(customDir, outputPath.getParent());
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void multipleTargetsGrouped() throws IOException {
        Path target1Dir = Files.createDirectory(tempDir.resolve("module-a"));
        Path target2Dir = Files.createDirectory(tempDir.resolve("module-b"));
        String file1 = target1Dir.resolve("A.java").toString();
        String file2 = target2Dir.resolve("B.java").toString();

        ScanTarget target1 = new ScanTarget("mod-a", target1Dir.toString(), "backend", "java", List.of(), List.of());
        ScanTarget target2 = new ScanTarget("mod-b", target2Dir.toString(), "backend", "java", List.of(), List.of());
        ProjectManifest manifest = new ProjectManifest(List.of(target1, target2), null,
            new OutputConfig(tempDir.toString(), "multi.json", null));

        AnalysisResult result1 = new AnalysisResult(file1, List.of(
            new ComponentInfo("Service", "AService", "com.a", file1)
        ));
        AnalysisResult result2 = new AnalysisResult(file2, List.of(
            new ComponentInfo("Service", "BService", "com.b", file2)
        ));

        Path outputPath = writer.write(manifest, List.of(result1, result2));
        JsonNode root = mapper.readTree(outputPath.toFile());
        JsonNode targets = root.get("targets");

        assertEquals(2, targets.size());
        assertEquals("mod-a", targets.get(0).get("name").asText());
        assertEquals("mod-b", targets.get(1).get("name").asText());

        assertEquals(1, targets.get(0).get("components").size());
        assertEquals("AService", targets.get(0).get("components").get(0).get("className").asText());
        assertEquals(1, targets.get(1).get("components").size());
        assertEquals("BService", targets.get(1).get("components").get(0).get("className").asText());
    }

    @Test
    void resultsOutsideTargetAreExcluded() throws IOException {
        Path targetDir = Files.createDirectory(tempDir.resolve("module-a"));
        Path outsideDir = Files.createDirectory(tempDir.resolve("other"));
        String insideFile = targetDir.resolve("A.java").toString();
        String outsideFile = outsideDir.resolve("Other.java").toString();

        ScanTarget target = new ScanTarget("mod-a", targetDir.toString(), "backend", "java", List.of(), List.of());
        ProjectManifest manifest = new ProjectManifest(List.of(target), null,
            new OutputConfig(tempDir.toString(), "filtered.json", null));

        AnalysisResult insideResult = new AnalysisResult(insideFile, List.of(
            new ComponentInfo("Service", "AService", "com.a", insideFile)
        ));
        AnalysisResult outsideResult = new AnalysisResult(outsideFile, List.of(
            new ComponentInfo("Service", "OtherService", "com.other", outsideFile)
        ));

        Path outputPath = writer.write(manifest, List.of(insideResult, outsideResult));
        JsonNode root = mapper.readTree(outputPath.toFile());
        assertEquals(1, root.get("targets").get(0).get("components").size());
        assertEquals("AService", root.get("targets").get(0).get("components").get(0).get("className").asText());
    }

    @Test
    void unwritableDirectoryThrowsError() {
        Path readOnlyDir = tempDir.resolve("readonly");
        readOnlyDir.toFile().mkdir();
        readOnlyDir.toFile().setWritable(false);

        ScanTarget target = new ScanTarget("app", tempDir.resolve("src").toString(), "backend", "java", List.of(), List.of());
        ProjectManifest manifest = new ProjectManifest(List.of(target), null,
            new OutputConfig(readOnlyDir.toString(), "index.json", null));

        IOException exception = assertThrows(IOException.class, () -> writer.write(manifest, List.of()));
        assertTrue(exception.getMessage().toLowerCase().contains("writ"));
    }

    @Test
    void emptyFindingsKeysOmittedFromJson() throws IOException {
        Path targetDir = Files.createDirectory(tempDir.resolve("proj"));
        String filePath = targetDir.resolve("App.java").toString();
        ScanTarget target = new ScanTarget("app", targetDir.toString(), "backend", "java", List.of(), List.of());
        ProjectManifest manifest = new ProjectManifest(List.of(target), null,
            new OutputConfig(tempDir.toString(), "sparse.json", null));

        AnalysisResult result = new AnalysisResult(filePath, List.of(
            new ComponentInfo("Service", "MyService", "com.app", filePath)
        ));

        Path outputPath = writer.write(manifest, List.of(result));
        JsonNode root = mapper.readTree(outputPath.toFile());
        JsonNode targetNode = root.get("targets").get(0);

        assertTrue(targetNode.has("components"));
        assertFalse(targetNode.has("endpoints"));
        assertFalse(targetNode.has("scheduled_tasks"));
        assertFalse(targetNode.has("event_listeners"));
        assertFalse(targetNode.has("validators"));
        assertFalse(targetNode.has("kafka_listeners"));
        assertFalse(targetNode.has("bean_methods"));
        assertFalse(targetNode.has("rabbitmq_listeners"));
        assertFalse(targetNode.has("activemq_listeners"));
    }
}
