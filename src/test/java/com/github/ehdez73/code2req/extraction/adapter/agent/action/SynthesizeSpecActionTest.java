package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import com.github.ehdez73.code2req.extraction.domain.model.BusinessRule;
import com.github.ehdez73.code2req.extraction.domain.model.ComplexityLevel;
import com.github.ehdez73.code2req.extraction.domain.model.EdgeCase;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationship;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationshipType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.GapReason;
import com.github.ehdez73.code2req.extraction.domain.model.GherkinScenario;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;
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

    private SynthesizeSpecAction action;
    private EntryPoint entryPoint;
    private FlowStep step1;
    private FlowStep step2;

    @BeforeEach
    void setUp() {
        action = new SynthesizeSpecAction(tempDir);
        entryPoint = new EntryPoint("GET /test", EntryPointType.HTTP, "GET", "/test",
            "TestController", "handle", "/src/TestController.java",
            0.5, false, List.of(), null, null);
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
        var gaps = List.of(new AmbiguityGap("flow-x", "/f.java", "Missing information", "Review manually", 0.3, GapReason.LOW_CONFIDENCE));

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
        var eventListenerEp = new EntryPoint("OrderPlacedEvent", EntryPointType.EVENT_LISTENER, null, null,
            "OrderEventListener", "handleOrderPlaced", "/src/OrderEventListener.java",
            0.0, false, List.of(), null, null);
        var serviceStep = new FlowStep(0, FlowStepComponentType.SERVICE,
            "OrderService", "processOrder", null, "/src/OrderService.java", 0, 0, List.of());
        var flow = new FunctionalFlow("flow-1", "Order Event Flow", eventListenerEp, List.of(serviceStep),
            null, List.of(), List.of(), List.of(),
            ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Order Event Feature", "Listens for order events", List.of(flow), List.of());
        var result = new CrossReferencedResult(List.of(feature), List.of());

        var specResult = action.synthesize(result, List.of(), List.of(), null);
        var content = Files.readString(specResult.markdownPath());

        assertTrue(content.contains("Event/Message Details"));
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
}
