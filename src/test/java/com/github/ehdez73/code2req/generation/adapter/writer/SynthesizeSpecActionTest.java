package com.github.ehdez73.code2req.generation.adapter.writer;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import com.github.ehdez73.code2req.extraction.domain.model.BusinessRule;
import com.github.ehdez73.code2req.extraction.domain.model.ComplexityLevel;
import com.github.ehdez73.code2req.extraction.domain.model.EdgeCase;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EventListenerEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationship;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationshipType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.GapReason;
import com.github.ehdez73.code2req.extraction.domain.model.GherkinScenario;
import com.github.ehdez73.code2req.extraction.domain.model.KafkaEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SynthesizeSpecActionTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper testMapper = new ObjectMapper();
    private SynthesizeSpecAction action;
    private EntryPoint entryPoint;
    private FlowStep step1;
    private FlowStep step2;

    @BeforeEach
    void setUp() {
        action = new SynthesizeSpecAction(tempDir);
        entryPoint = new HttpEntryPoint("GET /test", "TestController", "handle", "/src/TestController.java",
            0.5, false, "GET", "/test", List.of(), List.of());
        step1 = new FlowStep(0, FlowStepComponentType.REST_ENDPOINT,
            "TestController", "handle", null, "/src/TestController.java", 0, 0, List.of());
        step2 = new FlowStep(1, FlowStepComponentType.SERVICE,
            "OrderService", "process", null, "/src/OrderService.java", 0, 0, List.of());
    }

    @Test
    void synthesizeWritesFiles() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);

        assertTrue(Files.exists(specResult.markdownPath()));
        assertTrue(Files.exists(specResult.manifestPath()));
        assertEquals(1, specResult.featureCount());
        assertEquals(1, specResult.flowCount());
    }

    @Test
    void generateMarkdownContainsTitleAndFeature() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("# Generated Specification"));
        assertTrue(content.contains("Test Feature"));
        assertTrue(content.contains("Test Flow"));
    }

    @Test
    void generateMarkdownContainsFlowSteps() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Flow Steps"));
        assertTrue(content.contains("REST_ENDPOINT"));
        assertTrue(content.contains("OrderService"));
    }

    @Test
    void generateMarkdownIncludesDatabaseSection() throws IOException {
        var dbStep = new FlowStep(2, FlowStepComponentType.DATABASE,
            "OrderRepo", "save", null, "/src/OrderRepo.java", 0, 0, List.of("INSERT INTO orders"));
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2, dbStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Database Operations"));
        assertTrue(content.contains("INSERT INTO orders"));
    }

    @Test
    void generateMarkdownIncludesExternalSection() throws IOException {
        var extStep = new FlowStep(3, FlowStepComponentType.EXTERNAL_CALL,
            "PaymentClient", null, null, "/src/PaymentClient.java", 0, 0, List.of("POST /api/payment"));
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2, extStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("External Dependencies"));
        assertTrue(content.contains("POST"));
        assertTrue(content.contains("/api/payment"));
    }

    @Test
    void generateMarkdownIncludesCrossFlowRelationships() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var rel = new FlowRelationship("flow-1", "flow-2", FlowRelationshipType.DELEGATES_TO, "delegates to");
        var result = new CrossReferencedResult(List.of(feature), List.of(rel));

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Cross-Flow Relationships"));
        assertTrue(content.contains("DELEGATES_TO"));
    }

    @Test
    void generateMarkdownIncludesOrphanedMethods() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());
        var orphans = List.of(new OrphanedMethod("OrphanClass", "orphanMethod", "/src/Orphan.java", 0, 0, "Not reachable"));

        var specResult = action.synthesize(result, orphans, List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Orphaned Methods"));
        assertTrue(content.contains("OrphanClass"));
        assertTrue(content.contains("orphanMethod"));
    }

    @Test
    void generateMarkdownIncludesUnresolvedSection() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());
        var gaps = List.of(new AmbiguityGap("flow-x", "flow-x", "/f.java", "Missing information", "Review manually", 0.3, GapReason.LOW_CONFIDENCE));

        var specResult = action.synthesize(result, List.of(), gaps, null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Unresolved Dependencies"));
        assertTrue(content.contains("Missing information"));
    }

    @Test
    void generateMarkdownIncludesUserStory() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            "As a user I want to test", List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("As a user I want to test"));
    }

    @Test
    void generateMarkdownIncludesGherkin() throws IOException {
        var gherkin = new GherkinScenario("GS-001", "Success scenario",
            List.of("I am authenticated"), List.of("I call GET /test"), List.of("I get 200 OK"), "flow-1");
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(gherkin), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Success scenario"));
        assertTrue(content.contains("Given"));
        assertTrue(content.contains("When"));
        assertTrue(content.contains("Then"));
    }

    @Test
    void generateMarkdownIncludesBusinessRules() throws IOException {
        var rule = new BusinessRule("BR-001", "Must be authenticated", "User is logged in", "Request processed", "401 returned", "/src/TestController.java", 10, 20);
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(rule), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Business Rules"));
        assertTrue(content.contains("BR-001"));
    }

    @Test
    void generateMarkdownIncludesEdgeCases() throws IOException {
        var edgeCase = new EdgeCase("Empty request body", "Bad request response", "/src/TestController.java", 5, 8);
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(edgeCase),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Edge Cases"));
        assertTrue(content.contains("Empty request body"));
    }

    @Test
    void generateMarkdownIncludesMermaid() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.FULL, "graph TD\n    step0[TestController]");
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("mermaid"));
        assertTrue(content.contains("graph TD"));
    }

    @Test
    void generateMarkdownIncludesEventListenerEventType() throws IOException {
        var eventListenerEp = new EventListenerEntryPoint("OrderPlacedEvent", "OrderEventListener", "handleOrderPlaced", "/src/OrderEventListener.java",
            0.0, false, "OrderPlacedEvent");
        var serviceStep = new FlowStep(0, FlowStepComponentType.SERVICE,
            "OrderService", "processOrder", null, "/src/OrderService.java", 0, 0, List.of());
        var flow = new FunctionalFlow("flow-1", "Order Event Flow", eventListenerEp, List.of(serviceStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Order Event Feature", "Listens for order events", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Trigger Details"));
        assertTrue(content.contains("OrderPlacedEvent"));
    }

    @Test
    void generateManifestValidJson() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.manifestPath());

        assertTrue(content.contains("manifest_version"));
        assertTrue(content.contains("\"3.0.0\""));
        assertTrue(content.contains("feature-1"));
    }

    @Test
    void emptyInputProducesValidOutput() throws IOException {
        var result = new CrossReferencedResult(List.of(), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);

        assertTrue(Files.exists(specResult.markdownPath()));
        assertTrue(Files.exists(specResult.manifestPath()));
        assertEquals(0, specResult.featureCount());
        assertEquals(0, specResult.flowCount());
    }

    @Test
    void manifestHasStructuredEntryPoint() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var ep = root.get("features").get(0).get("flows").get(0).get("entry_point");
        assertTrue(ep.isObject());
        assertEquals("HTTP", ep.get("type").asText());
        assertEquals("TestController", ep.get("class_name").asText());
        assertEquals("handle", ep.get("method_name").asText());
        assertEquals("/src/TestController.java", ep.get("file_path").asText());
        assertEquals("GET", ep.get("http_method").asText());
        assertEquals("/test", ep.get("path").asText());
        assertTrue(ep.get("schedule").isNull());
        assertTrue(ep.get("topic_or_queue").isNull());
    }

    @Test
    void manifestScheduledEntryPointHasSchedule() throws IOException {
        var scheduledEp = new ScheduledEntryPoint("sched-1", "CleanupJob", "purgeOldRecords",
            "/src/CleanupJob.java", 0.5, false, "0 0 2 * * ?");
        var serviceStep = new FlowStep(0, FlowStepComponentType.SCHEDULED_TASK,
            "CleanupJob", "purgeOldRecords", null, "/src/CleanupJob.java", 10, 25, List.of());
        var flow = new FunctionalFlow("flow-1", "Cleanup", scheduledEp, List.of(serviceStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Scheduled Jobs", "desc", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var ep = root.get("features").get(0).get("flows").get(0).get("entry_point");
        assertEquals("SCHEDULED", ep.get("type").asText());
        assertEquals("0 0 2 * * ?", ep.get("schedule").asText());
        assertTrue(ep.get("http_method").isNull());
        assertTrue(ep.get("path").isNull());
    }

    @Test
    void manifestKafkaEntryPointHasTopic() throws IOException {
        var kafkaEp = new KafkaEntryPoint("kafka-1", "OrderHandler", "onOrderEvent",
            "/src/OrderHandler.java", 0.5, false, "order-events", false, "OrderEvent");
        var serviceStep = new FlowStep(0, FlowStepComponentType.SERVICE,
            "OrderHandler", "onOrderEvent", null, "/src/OrderHandler.java", 0, 0, List.of());
        var flow = new FunctionalFlow("flow-1", "Kafka Flow", kafkaEp, List.of(serviceStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Kafka Feature", "desc", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var ep = root.get("features").get(0).get("flows").get(0).get("entry_point");
        assertEquals("KAFKA", ep.get("type").asText());
        assertEquals("order-events", ep.get("topic_or_queue").asText());
    }

    @Test
    void manifestHasStepsArray() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var steps = root.get("features").get(0).get("flows").get(0).get("steps");
        assertTrue(steps.isArray());
        assertEquals(2, steps.size());
        assertEquals(0, steps.get(0).get("step_index").asInt());
        assertEquals("REST_ENDPOINT", steps.get(0).get("component_type").asText());
        assertEquals("TestController", steps.get(0).get("class_name").asText());
        assertEquals("handle", steps.get(0).get("method_name").asText());
        assertEquals("/src/TestController.java", steps.get(0).get("source_file").asText());
        assertEquals(1, steps.get(1).get("step_index").asInt());
        assertEquals("SERVICE", steps.get(1).get("component_type").asText());
    }

    @Test
    void manifestHasFullAcceptanceCriteria() throws IOException {
        var gherkin = new GherkinScenario("GS-001", "Success scenario",
            List.of("I am authenticated"), List.of("I call GET /test"), List.of("I get 200 OK"), "flow-1");
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(gherkin), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var ac = root.get("features").get(0).get("flows").get(0).get("acceptance_criteria").get(0);
        assertEquals("GS-001", ac.get("scenario_id").asText());
        assertEquals("Success scenario", ac.get("name").asText());
        assertEquals(1, ac.get("given").size());
        assertEquals("I am authenticated", ac.get("given").get(0).asText());
        assertEquals(1, ac.get("when").size());
        assertEquals("I call GET /test", ac.get("when").get(0).asText());
        assertEquals(1, ac.get("then").size());
        assertEquals("I get 200 OK", ac.get("then").get(0).asText());
    }

    @Test
    void manifestHasFullBusinessRules() throws IOException {
        var rule = new BusinessRule("BR-001", "Must be authenticated",
            "User is logged in", "Request processed", "401 returned",
            "/src/TestController.java", 10, 20);
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(rule), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var br = root.get("features").get(0).get("flows").get(0).get("business_rules").get(0);
        assertEquals("BR-001", br.get("rule_id").asText());
        assertEquals("Must be authenticated", br.get("description").asText());
        assertEquals("User is logged in", br.get("precondition").asText());
        assertEquals("Request processed", br.get("postcondition").asText());
        assertEquals("401 returned", br.get("error_behavior").asText());
        assertEquals("/src/TestController.java", br.get("source_file").asText());
        assertEquals(10, br.get("start_line").asInt());
        assertEquals(20, br.get("end_line").asInt());
    }

    @Test
    void manifestHasFullEdgeCases() throws IOException {
        var edgeCase = new EdgeCase("Empty request body", "Bad request response",
            "/src/TestController.java", 5, 8);
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(edgeCase),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var ec = root.get("features").get(0).get("flows").get(0).get("edge_cases").get(0);
        assertEquals("Empty request body", ec.get("scenario").asText());
        assertEquals("Bad request response", ec.get("business_consequence").asText());
        assertEquals("/src/TestController.java", ec.get("source_file").asText());
    }

    @Test
    void manifestIncludesMermaidDiagram() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.FULL, "graph TD\n    A[Test]");
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var mermaid = root.get("features").get(0).get("flows").get(0).get("mermaid_diagram");
        assertNotNull(mermaid);
        assertTrue(mermaid.asText().contains("graph TD"));
    }

    @Test
    void manifestOmitsMermaidWhenNull() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        assertNull(root.get("features").get(0).get("flows").get(0).get("mermaid_diagram"));
    }

    @Test
    void manifestReviewRequiredDefaultFalse() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        assertFalse(root.get("features").get(0).get("flows").get(0).get("review_required").asBoolean());
    }

    @Test
    void manifestReviewRequiredTrueWithGap() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());
        var gaps = List.of(new AmbiguityGap("flow-1", "Test Flow", "/f.java",
            "Missing context", "Review manually", 0.3, GapReason.LOW_CONFIDENCE));

        var specResult = action.synthesize(result, List.of(), gaps, null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var flowNode = root.get("features").get(0).get("flows").get(0);
        assertTrue(flowNode.get("review_required").asBoolean());
        var reason = flowNode.get("unresolved_reason");
        assertNotNull(reason);
        assertEquals("LOW_CONFIDENCE", reason.get("reason_type").asText());
        assertEquals("Missing context", reason.get("detail").asText());
        assertEquals(0.3, reason.get("confidence").asDouble(), 0.001);
    }

    @Test
    void manifestHasTraceabilityGraph() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var graph = root.get("features").get(0).get("flows").get(0).get("traceability_graph");
        assertNotNull(graph);
        assertTrue(graph.has("nodes"));
        assertTrue(graph.has("edges"));

        var nodes = graph.get("nodes");
        assertEquals(2, nodes.size());
        assertEquals("TestController.handle", nodes.get(0).get("node_id").asText());
        assertEquals("/src/TestController.java", nodes.get(0).get("file_reference").asText());
        assertEquals("OrderService.process", nodes.get(1).get("node_id").asText());

        var edges = graph.get("edges");
        assertEquals(1, edges.size());
        assertEquals("TestController.handle", edges.get(0).get("source_node").asText());
        assertEquals("OrderService.process", edges.get(0).get("target_node").asText());
        assertEquals("DETERMINISTIC_CALL", edges.get(0).get("link_type").asText());
    }

    @Test
    void manifestTraceabilityGraphExternalCallEdge() throws IOException {
        var extStep = new FlowStep(1, FlowStepComponentType.EXTERNAL_CALL,
            "PaymentClient", "charge", null, "/src/PaymentClient.java", 10, 25, List.of("POST /api/charge"));
        var flow = new FunctionalFlow("flow-1", "Payment", entryPoint, List.of(step1, extStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Payment Feature", "desc", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var edges = root.get("features").get(0).get("flows").get(0).get("traceability_graph").get("edges");
        assertEquals("FLOATING_HTTP", edges.get(0).get("link_type").asText());
    }

    @Test
    void manifestTraceabilityGraphDatabaseEdge() throws IOException {
        var dbStep = new FlowStep(1, FlowStepComponentType.DATABASE,
            "OrderRepo", "save", null, "/src/OrderRepo.java", 0, 0, List.of("INSERT INTO orders"));
        var flow = new FunctionalFlow("flow-1", "DB Flow", entryPoint, List.of(step1, dbStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "DB Feature", "desc", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var edges = root.get("features").get(0).get("flows").get(0).get("traceability_graph").get("edges");
        assertEquals("DATABASE_CALL", edges.get(0).get("link_type").asText());
    }

    @Test
    void manifestTraceabilityGraphEventPublisherEdge() throws IOException {
        var kafkaEp = new KafkaEntryPoint("kafka-1", "OrderHandler", "onOrderEvent",
            "/src/OrderHandler.java", 0.5, false, "order-events", false, "OrderEvent");
        var pubStep = new FlowStep(1, FlowStepComponentType.EVENT_PUBLISHER,
            "OrderService", "publishEvent", null, "/src/OrderService.java", 0, 0, List.of());
        var flow = new FunctionalFlow("flow-1", "Event Flow", kafkaEp, List.of(step1, pubStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Event Feature", "desc", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var edges = root.get("features").get(0).get("flows").get(0).get("traceability_graph").get("edges");
        assertEquals("TOPIC_KAFKA", edges.get(0).get("link_type").asText());
    }

    @Test
    void manifestOrphanedMethodsUseCorrectFieldNames() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());
        var orphans = List.of(new OrphanedMethod("OrphanClass", "orphanMethod",
            "/src/Orphan.java", 10, 20, "Not reachable"));

        var specResult = action.synthesize(result, orphans, List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var orphan = root.get("orphaned_methods").get(0);
        assertEquals("OrphanClass", orphan.get("class_name").asText());
        assertEquals("orphanMethod", orphan.get("method_name").asText());
        assertEquals("/src/Orphan.java", orphan.get("file_path").asText());
        assertEquals(10, orphan.get("start_line").asInt());
        assertEquals(20, orphan.get("end_line").asInt());
        assertEquals("Not reachable", orphan.get("reason").asText());
    }

    @Test
    void manifestCrossFlowRelationshipsUseCorrectFieldNames() throws IOException {
        var flow = new FunctionalFlow("flow-1", "Test Flow", entryPoint, List.of(step1, step2),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Test Feature", "A test feature", List.of(flow), List.of());
        var rel = new FlowRelationship("flow-1", "flow-2",
            FlowRelationshipType.PUBLISHES_EVENT, "publishes event");
        var result = new CrossReferencedResult(List.of(feature), List.of(rel));

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var root = testMapper.readTree(Files.readString(specResult.manifestPath()));

        var crossRel = root.get("cross_flow_relationships").get(0);
        assertEquals("flow-1", crossRel.get("source_flow_id").asText());
        assertEquals("flow-2", crossRel.get("target_flow_id").asText());
        assertEquals("PUBLISHES_EVENT", crossRel.get("type").asText());
        assertEquals("publishes event", crossRel.get("description").asText());
    }

}
