package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticEnrichmentTest {

    private static ExecutionFinding finding(String filePath) {
        return new ExecutionFinding(
            new ExecutionFinding.Metadata("t1", "test", filePath, "java", "mod", "now"),
            new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
            List.of(),
            new ExecutionFinding.ArchitecturalConnections(
                new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                new ExecutionFinding.Outbound(List.of(), List.of())),
            List.of());
    }

    private static ExecutionFinding findingWithTestInsight(String filePath, String testFilePath) {
        return new ExecutionFinding(
            new ExecutionFinding.Metadata("t1", "test", filePath, "java", "mod", "now"),
            new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
            List.of(new ExecutionFinding.TestInsight(testFilePath, "verifies flow", "hidden rule")),
            new ExecutionFinding.ArchitecturalConnections(
                new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                new ExecutionFinding.Outbound(List.of(), List.of())),
            List.of());
    }

    @Test
    void emptyConstructor() {
        var enrichment = new SemanticEnrichment();
        assertTrue(enrichment.findByFilePath("/any").isEmpty());
        assertTrue(enrichment.all().isEmpty());
        assertEquals(0, enrichment.size());
    }

    @Test
    void findByFilePathReturnsExisting() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java")));

        assertTrue(enrichment.findByFilePath("/src/A.java").isPresent());
    }

    @Test
    void findByFilePathReturnsEmptyForMissing() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java")));

        assertTrue(enrichment.findByFilePath("/src/B.java").isEmpty());
    }

    @Test
    void allReturnsAllFindings() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java"),
            "/src/B.java", finding("/src/B.java")));

        assertEquals(2, enrichment.all().size());
    }

    @Test
    void sizeMatchesEntryCount() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java"),
            "/src/B.java", finding("/src/B.java")));

        assertEquals(2, enrichment.size());
    }

    @Test
    void getAllTestInsightsAcrossAllFiles() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "/src/ATest.java"),
            "/src/B.java", findingWithTestInsight("/src/B.java", "/src/BTest.java")));

        var insights = enrichment.getAllTestInsights();

        assertEquals(2, insights.size());
        assertTrue(insights.stream().anyMatch(i -> i.testFilePath().equals("/src/ATest.java")));
        assertTrue(insights.stream().anyMatch(i -> i.testFilePath().equals("/src/BTest.java")));
    }

    @Test
    void getAllTestInsightsWithEmptyReturnsEmpty() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java")));

        assertTrue(enrichment.getAllTestInsights().isEmpty());
    }

    @Test
    void getTestFilePathReturnsPathWhenPresent() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "/src/ATest.java")));

        assertTrue(enrichment.getTestFilePath("/src/A.java").isPresent());
        assertEquals("/src/ATest.java", enrichment.getTestFilePath("/src/A.java").get());
    }

    @Test
    void getTestFilePathReturnsEmptyWhenNoFile() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java")));

        assertTrue(enrichment.getTestFilePath("/src/A.java").isEmpty());
    }

    @Test
    void getTestFilePathReturnsEmptyForUnknownPath() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "/src/ATest.java")));

        assertTrue(enrichment.getTestFilePath("/src/B.java").isEmpty());
    }

    @Test
    void getAllTestFilePathsReturnsMapping() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "/src/ATest.java"),
            "/src/B.java", findingWithTestInsight("/src/B.java", "/src/BTest.java")));

        var mapping = enrichment.getAllTestFilePaths();

        assertEquals(2, mapping.size());
        assertEquals("/src/ATest.java", mapping.get("/src/A.java"));
        assertEquals("/src/BTest.java", mapping.get("/src/B.java"));
    }

    @Test
    void getAllTestFilePathsExcludesFilesWithoutTestInsights() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "/src/ATest.java"),
            "/src/B.java", finding("/src/B.java")));

        var mapping = enrichment.getAllTestFilePaths();

        assertEquals(1, mapping.size());
        assertEquals("/src/ATest.java", mapping.get("/src/A.java"));
    }

    @Test
    void constructorDefensivelyCopiesMap() {
        var inner = new java.util.HashMap<String, ExecutionFinding>();
        inner.put("/src/A.java", finding("/src/A.java"));
        var enrichment = new SemanticEnrichment(inner);

        inner.put("/src/B.java", finding("/src/B.java"));

        assertEquals(1, enrichment.size());
    }

    @Test
    void returnedMapIsUnmodifiable() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java")));

        assertThrows(UnsupportedOperationException.class, () -> enrichment.all().add(null));
    }
}
