package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiscoverEntryPointsActionTest {

    @Test
    void discoverWithEmptyGraphReturnsEmpty() {
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry());
        var action = new DiscoverEntryPointsAction(knowledge);

        var result = action.discover();

        assertTrue(result.entryPoints().isEmpty());
        assertTrue(result.orphanedMethods().isEmpty());
    }

    @Test
    void discoverReturnsHttpEndpoints() {
        var endpoints = List.of(
            new EndpointInfo("GET", "/api/orders", "OrderController", List.of(), List.of(), "/src/OrderController.java", false, null));
        var graph = new StructuralGraph(List.of(), endpoints, List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var result = new DiscoverEntryPointsAction(knowledge).discover();

        assertEquals(1, result.entryPoints().size());
        assertEquals(EntryPointType.HTTP, result.entryPoints().get(0).type());
        assertEquals("GET", result.entryPoints().get(0).httpMethod());
        assertEquals("/api/orders", result.entryPoints().get(0).path());
    }

    @Test
    void discoverReturnsScheduledTasks() {
        var scheduled = List.of(
            new ScheduledTaskInfo("processOrders", "OrderScheduler", "0 0 * * *", null, null, "cron", "/src/OrderScheduler.java"));
        var graph = new StructuralGraph(List.of(), List.of(), List.of(), List.of(), scheduled, List.of(), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var result = new DiscoverEntryPointsAction(knowledge).discover();

        assertEquals(1, result.entryPoints().size());
        assertEquals(EntryPointType.SCHEDULED, result.entryPoints().get(0).type());
    }

    @Test
    void discoverFiltersTrivialActuatorPaths() {
        var endpoints = List.of(
            new EndpointInfo("GET", "/actuator/health", "HealthController", List.of(), List.of(), "/src/HealthController.java", false, null),
            new EndpointInfo("GET", "/api/orders", "OrderController", List.of(), List.of(), "/src/OrderController.java", false, null));
        var graph = new StructuralGraph(List.of(), endpoints, List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var result = new DiscoverEntryPointsAction(knowledge).discover();

        assertEquals(1, result.entryPoints().size());
        assertEquals("/api/orders", result.entryPoints().get(0).path());
    }

    @Test
    void discoverFiltersTrivialHealthClassNames() {
        var scheduled = List.of(
            new ScheduledTaskInfo("check", "HealthChecker", "0 * * * *", null, null, "cron", "/src/HealthChecker.java"),
            new ScheduledTaskInfo("report", "MetricsReporter", "0 * * * *", null, null, "cron", "/src/MetricsReporter.java"),
            new ScheduledTaskInfo("process", "OrderScheduler", "0 0 * * *", null, null, "cron", "/src/OrderScheduler.java"));
        var graph = new StructuralGraph(List.of(), List.of(), List.of(), List.of(), scheduled, List.of(), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var result = new DiscoverEntryPointsAction(knowledge).discover();

        assertEquals(1, result.entryPoints().size());
        assertEquals("OrderScheduler", result.entryPoints().get(0).className());
    }

    @Test
    void discoverSortsByPriorityDescending() {
        var endpoints = List.of(
            new EndpointInfo("GET", "/api/a", "AController", List.of(), List.of(), "/src/AController.java", false, null),
            new EndpointInfo("GET", "/api/b", "BController", List.of(), List.of(), "/src/BController.java", false, null));
        var graph = new StructuralGraph(List.of(), endpoints, List.of(), List.of());
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/AController.java", new ExecutionFinding(
                new ExecutionFinding.Metadata("t1", "test", "", "java", "mod", "now"),
                new ExecutionFinding.BusinessAbstraction("Purpose", List.of(
                    new ExecutionFinding.HappyPath("Flow", "Desc"))),
                new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
                List.of(),
                new ExecutionFinding.ArchitecturalConnections(
                    new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                    new ExecutionFinding.Outbound(List.of(), List.of())),
                List.of())));
        var knowledge = new CodebaseKnowledge(graph, enrichment, new LinkRegistry());

        var result = new DiscoverEntryPointsAction(knowledge).discover();

        assertEquals(2, result.entryPoints().size());
        assertTrue(result.entryPoints().get(0).priorityScore() >= result.entryPoints().get(1).priorityScore());
    }

    @Test
    void discoverDetectsOrphanedMethods() {
        var scheduled = List.of(
            new ScheduledTaskInfo("run", "ActiveScheduler", "0 * * * *", null, null, "cron", "/src/ActiveScheduler.java"),
            new ScheduledTaskInfo("report", "MetricsReporter", "0 * * * *", null, null, "cron", "/src/MetricsReporter.java"));
        var graph = new StructuralGraph(List.of(), List.of(), List.of(), List.of(), scheduled, List.of(), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var result = new DiscoverEntryPointsAction(knowledge).discover();

        assertTrue(result.orphanedMethods().stream().anyMatch(m -> m.className().equals("MetricsReporter")));
    }

    @Test
    void discoverWithReachableMethodsHasNoOrphans() {
        var edges = List.of(
            CallGraphEdge.resolved("Scheduler", "run", "/src/Scheduler.java", "OrderService", "process", "/src/OrderService.java", 1));
        var scheduled = List.of(
            new ScheduledTaskInfo("run", "Scheduler", "0 * * * *", null, null, "cron", "/src/Scheduler.java"));
        var graph = new StructuralGraph(edges, List.of(), List.of(), List.of(), scheduled, List.of(), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var result = new DiscoverEntryPointsAction(knowledge).discover();

        assertTrue(result.orphanedMethods().isEmpty());
    }
}
