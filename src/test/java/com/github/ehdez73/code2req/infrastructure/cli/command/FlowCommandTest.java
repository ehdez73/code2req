package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.extraction.ExtractionCache;
import com.github.ehdez73.code2req.extraction.ExtractionOrchestrator;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.domain.model.ComplexityLevel;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowCommandTest {

    @TempDir
    Path tempDir;

    private FlowCommand command;
    private ExtractionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = mock(ExtractionOrchestrator.class);
        command = new FlowCommand(orchestrator,
            tempDir.resolve("spec-output").toString(), "extraction-cache.json");
    }

    @Test
    void emptyStructuralGraphShowsNoDataMessage() {
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                new StructuralGraph(), null, null));

        String result = command.list(null, null, null, false);

        assertTrue(result.contains("No data"));
        assertTrue(result.contains("scan"));
    }

    @Test
    void showsPendingFlowsFromStructuralGraph() {
        var epInfo = new com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo(
            "GET", "/api/test", "TestController", "getTest",
            List.of(), List.of(), "/src/TestController.java", false, null, List.of());
        var graph = new StructuralGraph(List.of(), List.of(epInfo), List.of(), List.of());
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                graph, null, null));

        String result = command.list(null, null, null, false);

        assertTrue(result.contains("PENDING"));
        assertTrue(result.contains("GET /api/test"));
        assertTrue(result.contains("pending"), "Summary should show pending count");
    }

    @Test
    void showsAnalyzedFlowsFromCache() throws Exception {
        var graph = new StructuralGraph();
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                graph, null, null));

        var ep = new HttpEntryPoint("test-id", "Ctrl", "m", "/f.java",
            0.5, false, "GET", "/api/cached", List.of(), List.of());
        var flow = new FunctionalFlow(ep.id(), "GET /api/cached", ep, List.of(),
            "As a user...", List.of(), List.of(), List.of(), ComplexityLevel.STANDARD, null);
        var feature = new FunctionalFeature("f1", "Feature", "Desc", List.of(flow), List.of());
        var crossRef = new CrossReferencedResult(List.of(feature), List.of());
        var cache = new ExtractionCache(crossRef, List.of(), List.of());

        var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path cachePath = tempDir.resolve("spec-output/extraction-cache.json");
        java.nio.file.Files.createDirectories(cachePath.getParent());
        mapper.writeValue(cachePath.toFile(), cache);

        String result = command.list(null, null, null, false);

        assertTrue(result.contains("ANALYZED"));
        assertTrue(result.contains("GET /api/cached"));
    }

    @Test
    void statusFilterShowsOnlyMatching() {
        var epInfo = new com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo(
            "GET", "/api/test", "TestController", "getTest",
            List.of(), List.of(), "/src/TestController.java", false, null, List.of());
        var graph = new StructuralGraph(List.of(), List.of(epInfo), List.of(), List.of());
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                graph, null, null));

        String result = command.list("analyzed", null, null, false);

        assertTrue(result.contains("No flows match"));
    }

    @Test
    void typeFilterShowsOnlyMatching() {
        var epInfo = new com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo(
            "GET", "/api/test", "TestController", "getTest",
            List.of(), List.of(), "/src/TestController.java", false, null, List.of());
        var graph = new StructuralGraph(List.of(), List.of(epInfo), List.of(), List.of());
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                graph, null, null));

        String result = command.list(null, "http", null, false);

        assertTrue(result.contains("HTTP"));
        assertTrue(result.contains("GET /api/test"));
    }

    @Test
    void textFilterMatchesShortIdOrName() {
        var epInfo = new com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo(
            "GET", "/api/owners/{id}", "OwnerController", "getOwner",
            List.of(), List.of(), "/src/OwnerController.java", false, null, List.of());
        var graph = new StructuralGraph(List.of(), List.of(epInfo), List.of(), List.of());
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                graph, null, null));

        String result = command.list(null, null, "owner", false);

        assertTrue(result.contains("GET /api/owners/{id}"));
    }

    @Test
    void verboseShowsAdditionalDetails() {
        var epInfo = new com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo(
            "GET", "/api/test", "TestController", "getTest",
            List.of(), List.of(), "/src/TestController.java", false, null, List.of());
        var graph = new StructuralGraph(List.of(), List.of(epInfo), List.of(), List.of());
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                graph, null, null));

        String result = command.list(null, null, null, true);

        assertTrue(result.contains("steps="));
        assertTrue(result.contains("complexity="));
    }

    @Test
    void verboseShowsStaleLinksIndicator() throws Exception {
        var graph = new StructuralGraph();
        when(orchestrator.buildCodebaseKnowledge())
            .thenReturn(new com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge(
                graph, null, null));

        var ep = new HttpEntryPoint("stale-id", "Ctrl", "m", "/f.java",
            0.5, false, "GET", "/stale", List.of(), List.of());
        var flow = new FunctionalFlow(ep.id(), "GET /stale", ep, List.of(),
            "As a user...", List.of(), List.of(), List.of(), ComplexityLevel.STANDARD, null);
        var feature = new FunctionalFeature("f1", "Feature", "Desc", List.of(flow), List.of());
        var crossRef = new CrossReferencedResult(List.of(feature), List.of());
        var cache = new ExtractionCache(crossRef, List.of(), List.of(), Set.of(ep.id()));

        var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path cachePath = tempDir.resolve("spec-output/extraction-cache.json");
        java.nio.file.Files.createDirectories(cachePath.getParent());
        mapper.writeValue(cachePath.toFile(), cache);

        String result = command.list(null, null, null, true);

        assertTrue(result.contains("[stale links]"));
    }
}
