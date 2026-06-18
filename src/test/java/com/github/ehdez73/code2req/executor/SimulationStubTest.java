package com.github.ehdez73.code2req.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationStubTest {

    private final SimulationStub stub = new SimulationStub();
    private final ExecutionFindingValidator validator = new ExecutionFindingValidator();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsDeterministicResultExceptTimestamp() throws Exception {
        String result1 = stub.generateEnrichment("task-1", "/test.java", "test-module");
        String result2 = stub.generateEnrichment("task-1", "/test.java", "test-module");

        JsonNode node1 = MAPPER.readTree(result1);
        JsonNode node2 = MAPPER.readTree(result2);

        ((com.fasterxml.jackson.databind.node.ObjectNode) node1.get("metadata")).remove("timestamp");
        ((com.fasterxml.jackson.databind.node.ObjectNode) node2.get("metadata")).remove("timestamp");

        assertEquals(node1, node2);
    }

    @Test
    void differentInputsProduceDifferentResults() throws Exception {
        String result1 = stub.generateEnrichment("task-1", "/test.java", "test-module");
        String result2 = stub.generateEnrichment("task-2", "/other.java", "other-module");

        JsonNode node1 = MAPPER.readTree(result1);
        JsonNode node2 = MAPPER.readTree(result2);

        assertEquals("task-1", node1.get("metadata").get("task_id").asText());
        assertEquals("task-2", node2.get("metadata").get("task_id").asText());
        assertNotEquals(node1.get("metadata"), node2.get("metadata"));
    }

    @Test
    void resultConformsToSchema() {
        String json = stub.generateEnrichment("task-1", "/test.java", "test-module");
        assertTrue(validator.isValid(json), "Stub output must conform to §4 schema");
    }

    @Test
    void resultContainsTaskId() throws Exception {
        String json = stub.generateEnrichment("task-abc", "/test.java", "test-module");
        var node = MAPPER.readTree(json);
        assertEquals("task-abc", node.get("metadata").get("task_id").asText());
    }

    @Test
    void resultContainsFilePath() throws Exception {
        String json = stub.generateEnrichment("task-1", "/src/main/TestService.java", "test-module");
        var node = MAPPER.readTree(json);
        assertEquals("/src/main/TestService.java", node.get("metadata").get("file_path").asText());
    }

    @Test
    void resultIsValidJson() {
        String json = stub.generateEnrichment("task-1", "/test.java", "test-module");
        assertDoesNotThrow(() -> MAPPER.readTree(json));
    }
}
