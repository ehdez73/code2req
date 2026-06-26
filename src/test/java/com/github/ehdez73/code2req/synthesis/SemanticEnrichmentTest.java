package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.model.ExecutionFinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticEnrichmentTest {

    private static ExecutionFinding finding(String filePath, String flowName) {
        return new ExecutionFinding(
            new ExecutionFinding.Metadata("t1", "test", filePath, "java", "mod", "now"),
            new ExecutionFinding.BusinessAbstraction(
                "Purpose",
                List.of(new ExecutionFinding.HappyPath(flowName, "Description"))),
            new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
            List.of(),
            new ExecutionFinding.ArchitecturalConnections(
                new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                new ExecutionFinding.Outbound(List.of(), List.of())),
            List.of());
    }

    private static ExecutionFinding findingWithTestInsight(String filePath, String flowName, String testFilePath) {
        return new ExecutionFinding(
            new ExecutionFinding.Metadata("t1", "test", filePath, "java", "mod", "now"),
            new ExecutionFinding.BusinessAbstraction(
                "Purpose",
                List.of(new ExecutionFinding.HappyPath(flowName, "Description"))),
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
            "/src/A.java", finding("/src/A.java", "Flow A")));

        assertTrue(enrichment.findByFilePath("/src/A.java").isPresent());
        assertEquals("Flow A", enrichment.findByFilePath("/src/A.java").get()
            .businessAbstraction().happyPaths().get(0).flowName());
    }

    @Test
    void findByFilePathReturnsEmptyForMissing() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "Flow A")));

        assertTrue(enrichment.findByFilePath("/src/B.java").isEmpty());
    }

    @Test
    void allReturnsAllFindings() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "Flow A"),
            "/src/B.java", finding("/src/B.java", "Flow B")));

        assertEquals(2, enrichment.all().size());
    }

    @Test
    void getAllHappyPathsAcrossFiles() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "Flow A"),
            "/src/B.java", finding("/src/B.java", "Flow B")));

        var paths = enrichment.getAllHappyPaths();

        assertEquals(2, paths.size());
        assertTrue(paths.stream().anyMatch(p -> p.flowName().equals("Flow A")));
        assertTrue(paths.stream().anyMatch(p -> p.flowName().equals("Flow B")));
    }

    @Test
    void getFlowNamesReturnsDistinct() {
        var a = finding("/src/A.java", "Flow X");
        var b = new ExecutionFinding(
            new ExecutionFinding.Metadata("t2", "test", "/src/B.java", "java", "mod", "now"),
            new ExecutionFinding.BusinessAbstraction(
                "Purpose",
                List.of(new ExecutionFinding.HappyPath("Flow X", "Also X"))),
            new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
            List.of(),
            new ExecutionFinding.ArchitecturalConnections(
                new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                new ExecutionFinding.Outbound(List.of(), List.of())),
            List.of());

        var enrichment = new SemanticEnrichment(Map.of("/src/A.java", a, "/src/B.java", b));

        var names = enrichment.getFlowNames();

        assertEquals(1, names.size());
        assertEquals("Flow X", names.get(0));
    }

    @Test
    void sizeMatchesEntryCount() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "F1"),
            "/src/B.java", finding("/src/B.java", "F2")));

        assertEquals(2, enrichment.size());
    }

    @Test
    void getAllTestInsightsAcrossAllFiles() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "F1", "/src/ATest.java"),
            "/src/B.java", findingWithTestInsight("/src/B.java", "F2", "/src/BTest.java")));

        var insights = enrichment.getAllTestInsights();

        assertEquals(2, insights.size());
        assertTrue(insights.stream().anyMatch(i -> i.testFilePath().equals("/src/ATest.java")));
        assertTrue(insights.stream().anyMatch(i -> i.testFilePath().equals("/src/BTest.java")));
    }

    @Test
    void getAllTestInsightsWithEmptyReturnsEmpty() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "F1")));

        assertTrue(enrichment.getAllTestInsights().isEmpty());
    }

    @Test
    void getTestFilePathReturnsPathWhenPresent() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "F1", "/src/ATest.java")));

        assertTrue(enrichment.getTestFilePath("/src/A.java").isPresent());
        assertEquals("/src/ATest.java", enrichment.getTestFilePath("/src/A.java").get());
    }

    @Test
    void getTestFilePathReturnsEmptyWhenNoFile() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "F1")));

        assertTrue(enrichment.getTestFilePath("/src/A.java").isEmpty());
    }

    @Test
    void getTestFilePathReturnsEmptyForUnknownPath() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "F1", "/src/ATest.java")));

        assertTrue(enrichment.getTestFilePath("/src/B.java").isEmpty());
    }

    @Test
    void getAllTestFilePathsReturnsMapping() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "F1", "/src/ATest.java"),
            "/src/B.java", findingWithTestInsight("/src/B.java", "F2", "/src/BTest.java")));

        var mapping = enrichment.getAllTestFilePaths();

        assertEquals(2, mapping.size());
        assertEquals("/src/ATest.java", mapping.get("/src/A.java"));
        assertEquals("/src/BTest.java", mapping.get("/src/B.java"));
    }

    @Test
    void getAllTestFilePathsExcludesFilesWithoutTestInsights() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "F1", "/src/ATest.java"),
            "/src/B.java", finding("/src/B.java", "F2")));

        var mapping = enrichment.getAllTestFilePaths();

        assertEquals(1, mapping.size());
        assertEquals("/src/ATest.java", mapping.get("/src/A.java"));
    }

    @Test
    void constructorDefensivelyCopiesMap() {
        var inner = new java.util.HashMap<String, ExecutionFinding>();
        inner.put("/src/A.java", finding("/src/A.java", "F1"));
        var enrichment = new SemanticEnrichment(inner);

        inner.put("/src/B.java", finding("/src/B.java", "F2"));

        assertEquals(1, enrichment.size());
    }

    @Test
    void returnedMapIsUnmodifiable() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "F1")));

        assertThrows(UnsupportedOperationException.class, () -> enrichment.all().add(null));
    }
}
