package com.github.ehdez73.code2req.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionFindingValidatorTest {

    private static ExecutionFindingValidator validator;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        validator = new ExecutionFindingValidator();
    }

    @Test
    void validJsonPassesValidation() throws Exception {
        String json = MAPPER.writeValueAsString(buildValidRoot());
        assertTrue(validator.isValid(json));
    }

    @Test
    void missingRequiredFieldFailsValidation() {
        String json = """
            {
                "metadata": {
                    "task_id": "test-1",
                    "target_name": "test",
                    "file_path": "/test.java",
                    "tech_profile": "java-spring",
                    "module_tag": "test",
                    "timestamp": "2026-01-01T00:00:00"
                },
                "business_abstraction": {
                    "purpose": "Test purpose",
                    "happy_paths": []
                }
            }
            """;
        assertFalse(validator.isValid(json));
    }

    @Test
    void invalidJsonThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            validator.validate("not json at all"));
    }

    @Test
    void emptyObjectsFailValidation() {
        String json = "{}";
        assertFalse(validator.isValid(json));
    }

    private Map<String, Object> buildValidRoot() {
        return Map.of(
            "metadata", Map.of(
                "task_id", "test-task-1",
                "target_name", "test-target",
                "file_path", "/src/test.java",
                "tech_profile", "java-spring",
                "module_tag", "test",
                "timestamp", LocalDateTime.now().toString()
            ),
            "business_abstraction", Map.of(
                "purpose", "Test purpose",
                "happy_paths", List.of(Map.of("flow_name", "Standard", "description", "Standard flow"))
            ),
            "business_rules_and_guardrails", Map.of(
                "validations", List.of(Map.of("field_or_context", "input", "rule", "Not null", "error_behavior", "Error")),
                "edge_cases", List.of(Map.of("scenario", "Null", "business_consequence", "Failure"))
            ),
            "test_insights", List.of(),
            "architectural_connections", Map.of(
                "inbound", Map.of("http_endpoints", List.of(), "event_subscriptions", List.of(), "scheduled_triggers", List.of()),
                "outbound", Map.of("http_calls", List.of(), "event_publications", List.of())
            ),
            "discovered_dependencies", List.of()
        );
    }
}
