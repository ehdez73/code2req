package com.github.ehdez73.code2req.generation.domain.model.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestPojoSchemaTest {

    private static JsonSchema schema;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void loadSchema() {
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try (InputStream is = ManifestPojoSchemaTest.class
                .getResourceAsStream("/schema/semantic-manifest-schema.json")) {
            assertNotNull(is, "schema/semantic-manifest-schema.json not found on classpath");
            schema = factory.getSchema(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load schema", e);
        }
    }

    @Test
    void fullManifestConformsToSchema() throws Exception {
        var manifest = buildFullManifest();
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);

        var violations = schema.validate(MAPPER.readTree(json));
        assertTrue(violations.isEmpty(),
            "Manifest JSON violates schema: " + violations);
    }

    @Test
    void minimalManifestConformsToSchema() throws Exception {
        var manifest = new SemanticManifest(
            "3.0.0",
            "minimal",
            Instant.now().toString(),
            List.of(),
            List.of(),
            List.of()
        );
        String json = MAPPER.writeValueAsString(manifest);

        var violations = schema.validate(MAPPER.readTree(json));
        assertTrue(violations.isEmpty(),
            "Minimal manifest violates schema: " + violations);
    }

    @Test
    void manifestWithAllNullableFieldsConformsToSchema() throws Exception {
        var manifest = buildFullManifest();
        String json = MAPPER.writeValueAsString(manifest);

        var violations = schema.validate(MAPPER.readTree(json));
        assertTrue(violations.isEmpty(),
            "Full manifest with nullable fields violates schema: " + violations);
    }

    private static SemanticManifest buildFullManifest() {
        return new SemanticManifest(
            "3.0.0",
            "test-system",
            Instant.now().toString(),
            List.of(buildFeature()),
            List.of(new ManifestCrossFlowRelationship(
                "flow-1", "flow-2", "DELEGATES_TO", "delegates to flow-2"
            )),
            List.of(new ManifestOrphanedMethod(
                "OrphanClass", "orphanMethod", "/src/Orphan.java", 10, 20, "Not reachable"
            ))
        );
    }

    private static ManifestFeature buildFeature() {
        return new ManifestFeature(
            "f-1",
            "Test Feature",
            "A feature with full coverage",
            List.of(buildFlow())
        );
    }

    private static ManifestFlow buildFlow() {
        return new ManifestFlow(
            "flow-1",
            buildEntryPoint(),
            List.of(buildStep()),
            "As a user I want to test",
            List.of(buildAcceptanceCriterion()),
            List.of(buildBusinessRule()),
            List.of(buildEdgeCase()),
            List.of(buildNfr()),
            "graph TD\n    A[Start]",
            "FULL",
            true,
            new ManifestUnresolvedReason("LOW_CONFIDENCE", "Missing context", 0.3),
            buildTraceabilityGraph()
        );
    }

    private static ManifestEntryPoint buildEntryPoint() {
        return new ManifestEntryPoint(
            "HTTP", "GET", "/api/test", "TestController", "handle",
            "/src/TestController.java", null, null
        );
    }

    private static ManifestStep buildStep() {
        return new ManifestStep(
            0, "REST_ENDPOINT", "TestController", "handle",
            "Handles test requests", "/src/TestController.java", 10, 25
        );
    }

    private static ManifestAcceptanceCriterion buildAcceptanceCriterion() {
        return new ManifestAcceptanceCriterion(
            "GS-001", "Success scenario",
            List.of("I am authenticated"),
            List.of("I call GET /api/test"),
            List.of("I get 200 OK")
        );
    }

    private static ManifestBusinessRule buildBusinessRule() {
        return new ManifestBusinessRule(
            "BR-001", "Must be authenticated",
            "User is logged in", "Request processed", "401 returned",
            "/src/TestController.java", 10, 20,
            new ManifestExternalCall("POST", "/api/payment", 5000, "exponential", "circuit-breaker")
        );
    }

    private static ManifestEdgeCase buildEdgeCase() {
        return new ManifestEdgeCase(
            "Empty request body", "Bad request response", "MEDIUM", "/src/TestController.java"
        );
    }

    private static ManifestNonFunctionalRequirement buildNfr() {
        return new ManifestNonFunctionalRequirement(
            "Performance", "Response time < 200ms", "/src/TestController.java"
        );
    }

    private static ManifestTraceabilityGraph buildTraceabilityGraph() {
        return new ManifestTraceabilityGraph(
            List.of(new ManifestTraceabilityNode(
                "TestController.handle", "/src/TestController.java", null, "10-25"
            )),
            List.of(new ManifestTraceabilityEdge(
                "TestController.handle", "OrderService.process", "DETERMINISTIC_CALL"
            ))
        );
    }
}
