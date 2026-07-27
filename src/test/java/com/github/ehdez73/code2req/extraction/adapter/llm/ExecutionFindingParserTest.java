package com.github.ehdez73.code2req.extraction.adapter.llm;

import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionFindingParserTest {

    private final ExecutionFindingParser parser = new ExecutionFindingParser();

    @Test
    void sanitizeRemovesCodeFence() {
        String input = "```json\n{\"key\": \"value\"}\n```";
        assertEquals("{\"key\": \"value\"}", parser.sanitize(input));
    }

    @Test
    void sanitizeRemovesCodeFenceWithoutTrailing() {
        String input = "```\n{\"key\": \"value\"}";
        assertEquals("{\"key\": \"value\"}", parser.sanitize(input));
    }

    @Test
    void sanitizeRemovesTrailingCommas() {
        String input = "{\"a\": 1,}";
        assertEquals("{\"a\": 1}", parser.sanitize(input));
    }

    @Test
    void sanitizeRemovesTrailingCommasInArray() {
        String input = "{\"a\": [1, 2,]}";
        assertEquals("{\"a\": [1, 2]}", parser.sanitize(input));
    }

    @Test
    void sanitizeFixesSpuriousQuotesBetweenObjects() {
        String result = parser.sanitize("}\"  {");
        assertFalse(result.contains("\""), "quotes should be removed: " + result);
    }

    @Test
    void sanitizeFixesSpuriousQuotesBetweenArrays() {
        String result = parser.sanitize("]\"  [");
        assertFalse(result.contains("\""), "quotes should be removed: " + result);
    }

    @Test
    void sanitizeFixesSpuriousQuotesObjectToArray() {
        String result = parser.sanitize("}\"  [");
        assertFalse(result.contains("\""), "quotes should be removed: " + result);
    }

    @Test
    void sanitizeFixesSpuriousQuotesArrayToObject() {
        String result = parser.sanitize("]\"  {");
        assertFalse(result.contains("\""), "quotes should be removed: " + result);
    }

    @Test
    void sanitizeFixesMissingCommasBetweenAdjacentLiterals() {
        String input = "{\"a\": 1}{\"b\": 2}";
        assertEquals("{\"a\": 1},{\"b\": 2}", parser.sanitize(input));
    }

    @Test
    void sanitizeFixesMissingCommasBetweenArrays() {
        String input = "[1][2]";
        assertEquals("[1],[2]", parser.sanitize(input));
    }

    @Test
    void sanitizeFixesMissingCommasMixed() {
        String input = "}[1]";
        assertEquals("},[1]", parser.sanitize(input));
    }

    @Test
    void sanitizeAddsMissingClosingBraces() {
        String input = "{\"a\": {\"b\": 1}";
        String result = parser.sanitize(input);
        assertTrue(result.endsWith("}") || result.endsWith("}\n}"));
    }

    @Test
    void normalizeReturnsValidJson() {
        String input = "{\"metadata\": {\"task_id\": \"t1\"}}";
        String result = parser.normalize(input);
        assertTrue(result.startsWith("{") && result.endsWith("}"));
    }

    @Test
    void normalizeReturnsOriginalOnInvalidJson() {
        String input = "not-json";
        assertEquals(input, parser.normalize(input));
    }

    @Test
    void parseLenientAddsDefaultsForMissingFields() {
        String json = """
            {"metadata": {"task_id": "t1", "target_name": "t", "file_path": "f.java",
            "tech_profile": "spring", "module_tag": "m", "timestamp": "2026-01-01T00:00:00"}}
            """;
        ExecutionFinding result = parser.parseLenient(json);
        assertNotNull(result);
        assertNotNull(result.businessRulesAndGuardrails());
        assertNotNull(result.testInsights());
        assertNotNull(result.architecturalConnections());
        assertNotNull(result.discoveredDependencies());
    }

    @Test
    void parseLenientThrowsOnInvalidJson() {
        assertThrows(RuntimeException.class, () -> parser.parseLenient("not-json"));
    }

    @Test
    void normalizeAddsDefaults() {
        String input = """
            {"metadata": {"task_id": "t1", "target_name": "t", "file_path": "f.java",
            "tech_profile": "spring", "module_tag": "m", "timestamp": "2026-01-01T00:00:00"}}
            """;
        String result = parser.normalize(input);
        assertTrue(result.contains("\"validations\""));
        assertTrue(result.contains("\"test_insights\""));
    }

    @Test
    void sanitizeHandlesEmptyString() {
        assertEquals("", parser.sanitize(""));
    }

    @Test
    void sanitizeHandlesWhitespaceOnly() {
        assertEquals("", parser.sanitize("   "));
    }

    @Test
    void sanitizeHandlesBraceImbalance() {
        String input = "{\"a\": {\"b\": {\"c\": 1}}";
        String result = parser.sanitize(input);
        assertEquals(countChar(result, '{'), countChar(result, '}'),
            "braces should be balanced");
    }

    @Test
    void sanitizeIgnoresCommasInStrings() {
        String input = "{\"a\": \"hello, world\"}";
        assertEquals(input, parser.sanitize(input));
    }

    @Test
    void normalizeHandlesRootWithTimestampMigration() {
        String input = """
            {"timestamp": "2026-01-01T00:00:00", "metadata": {"task_id": "t1", "target_name": "t",
            "file_path": "f.java", "tech_profile": "spring", "module_tag": "m"}}
            """;
        String result = parser.normalize(input);
        assertTrue(result.contains("\"timestamp\""));
    }

    @Test
    void normalizeConvertsDiscoveredDependenciesToObjects() {
        String input = """
            {"metadata": {"task_id": "t1", "target_name": "t", "file_path": "f.java",
            "tech_profile": "spring", "module_tag": "m", "timestamp": "2026-01-01T00:00:00"},
            "discovered_dependencies": ["file1.java", "file2.java"]}
            """;
        String result = parser.normalize(input);
        assertTrue(result.contains("\"file_path\""));
        assertTrue(result.contains("\"discovery_depth\""));
    }

    private static int countChar(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }
}
