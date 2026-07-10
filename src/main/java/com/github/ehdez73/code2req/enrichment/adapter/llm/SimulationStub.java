package com.github.ehdez73.code2req.enrichment.adapter.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class SimulationStub {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public String generateEnrichment(String taskId, String filePath, String targetName) {
        try {
            Map<String, Object> finding = buildFinding(taskId, filePath, targetName);
            return MAPPER.writeValueAsString(finding);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize stub enrichment", e);
        }
    }

    private Map<String, Object> buildFinding(String taskId, String filePath, String targetName) {
        return Map.of(
            "metadata", Map.of(
                "task_id", taskId,
                "target_name", targetName,
                "file_path", filePath,
                "tech_profile", "java-spring",
                "module_tag", targetName,
                "timestamp", LocalDateTime.now().toString()
            ),
            "business_rules_and_guardrails", Map.of(
                "validations", List.of(
                    Map.of("field_or_context", "input", "rule", "Input must not be null", "error_behavior", "Throws IllegalArgumentException")
                ),
                "edge_cases", List.of(
                    Map.of("scenario", "Null input", "business_consequence", "Request rejected with error response")
                )
            ),
            "test_insights", List.of(
                Map.of(
                    "test_file_path", testPath(filePath),
                    "scenario_verified", "Standard flow completes successfully",
                    "hidden_rule_uncovered", "Input validation enforces non-null constraint"
                )
            ),
            "architectural_connections", Map.of(
                "inbound", Map.of(
                    "http_endpoints", List.of(),
                    "event_subscriptions", List.of(),
                    "scheduled_triggers", List.of()
                ),
                "outbound", Map.of(
                    "http_calls", List.of(),
                    "event_publications", List.of()
                )
            ),
            "discovered_dependencies", List.of()
        );
    }

    private static String fileName(String filePath) {
        if (filePath == null) return "Unknown";
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private static String testPath(String filePath) {
        if (filePath == null) return "Unknown";
        return filePath.replace("/main/", "/test/").replace(".java", "Test.java");
    }
}
