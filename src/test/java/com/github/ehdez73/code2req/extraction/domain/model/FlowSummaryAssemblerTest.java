package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.extraction.ExtractionCache;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FlowSummaryAssemblerTest {

    @Test
    void emptyStructuralGraphReturnsEmptyList() {
        var graph = new StructuralGraph();
        var assembler = new FlowSummaryAssembler(graph, null);

        var result = assembler.assemble();

        assertTrue(result.isEmpty());
    }

    @Test
    void sqliteOnlyShowsPendingStatus() {
        var graph = new StructuralGraph(
            List.of(), List.of(new EndpointInfo("GET", "/api/users", "UsersController",
                "getUsers", List.of(), List.of(), "/src/UsersController.java", false, null, List.of())), List.of(), List.of());

        var assembler = new FlowSummaryAssembler(graph, null);
        var result = assembler.assemble();

        assertEquals(1, result.size());
        assertEquals(FlowSummaryStatus.PENDING, result.get(0).status());
        assertEquals(EntryPointType.HTTP, result.get(0).type());
        assertEquals("GET /api/users", result.get(0).name());
    }

    @Test
    void cacheOnlyShowsAnalyzedStatus() {
        var graph = new StructuralGraph();
        var ep = new HttpEntryPoint("GET /api/users", "UsersController", "getUsers",
            "/src/UsersController.java", 0.5, false, "GET", "/api/users", List.of(), List.of());
        var flow = new FunctionalFlow(ep.id(), "GET /api/users", ep, List.of(),
            "As a user...", List.of(), List.of(), List.of(), ComplexityLevel.STANDARD, null);
        var feature = new FunctionalFeature("f1", "User Management", "Manages users",
            List.of(flow), List.of());
        var crossRef = new CrossReferencedResult(List.of(feature), List.of());
        var cache = new ExtractionCache(crossRef, List.of(), List.of());

        var assembler = new FlowSummaryAssembler(graph, cache);
        var result = assembler.assemble();

        assertEquals(1, result.size());
        assertEquals(FlowSummaryStatus.ANALYZED, result.get(0).status());
        assertEquals("GET /api/users", result.get(0).name());
    }

    @Test
    void cacheTakesPrecedenceOverSqlite() {
        var epId = "/src/UsersController.java:UsersController:getUsers GET /api/users";
        var epInfo = new EndpointInfo("GET", "/api/users", "UsersController",
            "getUsers", List.of(), List.of(), "/src/UsersController.java", false, null, List.of());
        var graph = new StructuralGraph(List.of(), List.of(epInfo), List.of(), List.of());

        var ep = new HttpEntryPoint(epId,
            "UsersController", "getUsers", "/src/UsersController.java",
            0.5, false, "GET", "/api/users", List.of(), List.of());
        var flow = new FunctionalFlow(ep.id(), "GET /api/users", ep, List.of(),
            "As a user...", List.of(), List.of(), List.of(), ComplexityLevel.STANDARD, null);
        var feature = new FunctionalFeature("f1", "User Management", "Manages users",
            List.of(flow), List.of());
        var crossRef = new CrossReferencedResult(List.of(feature), List.of());
        var cache = new ExtractionCache(crossRef, List.of(), List.of());

        var assembler = new FlowSummaryAssembler(graph, cache);
        var result = assembler.assemble();

        assertEquals(1, result.size(), "Should not duplicate; cache takes precedence");
        assertEquals(FlowSummaryStatus.ANALYZED, result.get(0).status());
    }

    @Test
    void propagatesStaleLinks() {
        var graph = new StructuralGraph();
        var ep = new HttpEntryPoint("flow1", "Ctrl", "m", "/f.java",
            0.5, false, "GET", "/api", List.of(), List.of());
        var flow = new FunctionalFlow(ep.id(), "GET /api", ep, List.of(),
            "As a user...", List.of(), List.of(), List.of(), ComplexityLevel.STANDARD, null);
        var feature = new FunctionalFeature("f1", "Feature", "Desc", List.of(flow), List.of());
        var crossRef = new CrossReferencedResult(List.of(feature), List.of());
        var cache = new ExtractionCache(crossRef, List.of(), List.of(), Set.of(ep.id()));

        var assembler = new FlowSummaryAssembler(graph, cache);
        var result = assembler.assemble();

        assertEquals(1, result.size());
        assertTrue(result.get(0).hasStaleLinks());
    }

    @Test
    void mixedSqliteAndCacheShowsBothWhenDifferent() {
        var epInfo1 = new EndpointInfo("GET", "/api/a", "CtrlA", "mA", List.of(),
            List.of(), "/a.java", false, null, List.of());
        var graph = new StructuralGraph(List.of(), List.of(epInfo1), List.of(), List.of());

        var ep = new HttpEntryPoint("cacheId", "CtrlB", "mB", "/b.java",
            0.5, false, "POST", "/api/b", List.of(), List.of());
        var flow = new FunctionalFlow(ep.id(), "POST /api/b", ep, List.of(),
            "As a user...", List.of(), List.of(), List.of(), ComplexityLevel.STANDARD, null);
        var feature = new FunctionalFeature("f1", "Feature", "Desc", List.of(flow), List.of());
        var crossRef = new CrossReferencedResult(List.of(feature), List.of());
        var cache = new ExtractionCache(crossRef, List.of(), List.of());

        var assembler = new FlowSummaryAssembler(graph, cache);
        var result = assembler.assemble();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(f -> f.status() == FlowSummaryStatus.PENDING));
        assertTrue(result.stream().anyMatch(f -> f.status() == FlowSummaryStatus.ANALYZED));
    }
}
