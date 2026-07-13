package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.EntryPointDiscoveryResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.KafkaEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.extraction.domain.model.EventListenerEntryPoint;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraceFlowActionTest {

    @TempDir
    Path tempDir;

    private TraceFlowAction action(StructuralGraph graph) {
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());
        return new TraceFlowAction(knowledge, null);
    }

    private TraceFlowAction action(StructuralGraph graph, LinkRegistry registry) {
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), registry);
        return new TraceFlowAction(knowledge, null);
    }

    @Test
    void schedulerEntryPointClassifiedAsSCHEDULED_TASK() {
        var edge = CallGraphEdge.resolved("Scheduler", "performTask", "/src/Scheduler.java", "OrderService", "process", "/src/OrderService.java", 1);
        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of());
        var entryPoint = new ScheduledEntryPoint("performTask", "Scheduler", "performTask", "/src/Scheduler.java",
            0.5, false, "0 * * * *");

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        var steps = result.flows().get(0).steps();
        assertEquals(FlowStepComponentType.SCHEDULED_TASK, steps.get(0).componentType());
    }

    @Test
    void controllerEntryPointClassifiedAsREST_ENDPOINT() {
        var edge = CallGraphEdge.resolved("OrderController", "get", "/src/OrderController.java", "OrderService", "find", "/src/OrderService.java", 1);
        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "OrderController", "get", "/src/OrderController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(FlowStepComponentType.REST_ENDPOINT, result.flows().get(0).steps().get(0).componentType());
    }

    @Test
    void serviceEntryPointClassifiedAsSERVICE() {
        var edge = CallGraphEdge.resolved("EventService", "onEvent", "/src/EventService.java", "AuditRepo", "save", "/src/AuditRepo.java", 1);
        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of());
        var entryPoint = new EventListenerEntryPoint("MyEvent", "EventService", "onEvent", "/src/EventService.java",
            0.5, false, "MyEvent");

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(FlowStepComponentType.SERVICE, result.flows().get(0).steps().get(0).componentType());
    }

    @Test
    void repositoryEntryPointClassifiedAsREPOSITORY() {
        var edge = CallGraphEdge.resolved("AuditRepo", "save", "/src/AuditRepo.java", "JdbcTemplate", "execute", "/src/JdbcTemplate.java", 1);
        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of());
        var entryPoint = new ScheduledEntryPoint("AuditRepo.save", "AuditRepo", "save", "/src/AuditRepo.java",
            0.5, false, "0 0 * * *");

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(FlowStepComponentType.REPOSITORY, result.flows().get(0).steps().get(0).componentType());
    }

    @Test
    void listenerEntryPointClassifiedAsEVENT_PUBLISHER() {
        var edge = CallGraphEdge.resolved("OrderListener", "handle", "/src/OrderListener.java", "OrderService", "process", "/src/OrderService.java", 1);
        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of());
        var entryPoint = new KafkaEntryPoint("topic", "OrderListener", "handle", "/src/OrderListener.java",
            0.5, false, "topic", false, "");

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(FlowStepComponentType.EVENT_PUBLISHER, result.flows().get(0).steps().get(0).componentType());
    }

    @Test
    void downstreamControllerClassifiedAsREST_ENDPOINT() {
        var edge1 = CallGraphEdge.resolved("SomeController", "get", "/src/SomeController.java", "OrderService", "find", "/src/OrderService.java", 1);
        var edge2 = CallGraphEdge.resolved("OrderService", "find", "/src/OrderService.java", "UserController", "lookup", "/src/UserController.java", 1);
        var graph = new StructuralGraph(List.of(edge1, edge2), List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "SomeController", "get", "/src/SomeController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        var steps = result.flows().get(0).steps();
        var downstream = steps.stream().filter(s -> "UserController".equals(s.className())).findFirst().orElseThrow();
        assertEquals(FlowStepComponentType.REST_ENDPOINT, downstream.componentType());
    }

    @Test
    void downstreamSchedulerClassifiedAsSCHEDULED_TASK() {
        var edge1 = CallGraphEdge.resolved("SomeController", "get", "/src/SomeController.java", "OrderService", "find", "/src/OrderService.java", 1);
        var edge2 = CallGraphEdge.resolved("OrderService", "find", "/src/OrderService.java", "Scheduler", "run", "/src/Scheduler.java", 1);
        var graph = new StructuralGraph(List.of(edge1, edge2), List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "SomeController", "get", "/src/SomeController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        var steps = result.flows().get(0).steps();
        var downstream = steps.stream().filter(s -> "Scheduler".equals(s.className())).findFirst().orElseThrow();
        assertEquals(FlowStepComponentType.SCHEDULED_TASK, downstream.componentType());
    }

    @Test
    void downstreamClientClassifiedAsEXTERNAL_CALL() {
        var edge1 = CallGraphEdge.resolved("SomeController", "get", "/src/SomeController.java", "OrderService", "find", "/src/OrderService.java", 1);
        var edge2 = CallGraphEdge.resolved("OrderService", "find", "/src/OrderService.java", "PaymentClient", "charge", "/src/PaymentClient.java", 1);
        var graph = new StructuralGraph(List.of(edge1, edge2), List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "SomeController", "get", "/src/SomeController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        var steps = result.flows().get(0).steps();
        var downstream = steps.stream().filter(s -> "PaymentClient".equals(s.className())).findFirst().orElseThrow();
        assertEquals(FlowStepComponentType.EXTERNAL_CALL, downstream.componentType());
    }

    @Test
    void downstreamRepositoryClassifiedAsREPOSITORY() {
        var edge1 = CallGraphEdge.resolved("SomeController", "get", "/src/SomeController.java", "OrderService", "find", "/src/OrderService.java", 1);
        var edge2 = CallGraphEdge.resolved("OrderService", "find", "/src/OrderService.java", "OrderRepo", "findById", "/src/OrderRepo.java", 1);
        var graph = new StructuralGraph(List.of(edge1, edge2), List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "SomeController", "get", "/src/SomeController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        var steps = result.flows().get(0).steps();
        var downstream = steps.stream().filter(s -> "OrderRepo".equals(s.className())).findFirst().orElseThrow();
        assertEquals(FlowStepComponentType.REPOSITORY, downstream.componentType());
    }

    @Test
    void downstreamDefaultClassifiedAsSERVICE() {
        var edge1 = CallGraphEdge.resolved("OrderController", "get", "/src/OrderController.java", "Helper", "help", "/src/Helper.java", 1);
        var graph = new StructuralGraph(List.of(edge1), List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "OrderController", "get", "/src/OrderController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        var steps = result.flows().get(0).steps();
        var downstream = steps.stream().filter(s -> "Helper".equals(s.className())).findFirst().orElseThrow();
        assertEquals(FlowStepComponentType.SERVICE, downstream.componentType());
    }

    @Test
    void traceAllWithNoEntryPointsReturnsEmpty() {
        var result = action(new StructuralGraph()).traceAll(new EntryPointDiscoveryResult(List.of(), List.of()));
        assertTrue(result.flows().isEmpty());
    }

    @Test
    void traceAllProducesStepsForEntryPointsWithEdges() {
        var edges = List.of(
            CallGraphEdge.resolved("OrderController", "get", "/src/OrderController.java", "OrderService", "find", "/src/OrderService.java", 1));
        var graph = new StructuralGraph(edges, List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "OrderController", "get", "/src/OrderController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(1, result.flows().size());
        var flow = result.flows().get(0);
        assertEquals(2, flow.steps().size());
        assertEquals("OrderController", flow.steps().get(0).className());
        assertEquals("OrderService", flow.steps().get(1).className());
    }

    @Test
    void traceAllIncludesDatabaseSteps() {
        var edges = List.of(
            CallGraphEdge.resolved("OrderController", "get", "/src/OrderController.java", "OrderService", "find", "/src/OrderService.java", 1));
        var dbAccess = List.of(
            new DbAccessInfo("JPA", "SELECT * FROM orders", "orders", null, "find", "OrderService", "/src/OrderService.java", "Order", false, 0, 0, 1));
        var graph = new StructuralGraph(edges, List.of(), dbAccess, List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "OrderController", "get", "/src/OrderController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertTrue(result.flows().get(0).steps().stream().anyMatch(s -> s.componentType() == FlowStepComponentType.DATABASE));
    }

    @Test
    void traceAllIncludesExternalCallSteps() {
        var edges = List.of(
            CallGraphEdge.resolved("OrderController", "get", "/src/OrderController.java", "OrderService", "find", "/src/OrderService.java", 1));
        var graph = new StructuralGraph(edges, List.of(), List.of(), List.of());
        var floatingLinks = List.of(
            new FloatingLinkInfo("POST", "/api/payment", false, "RestTemplate", "/src/OrderService.java", "find", null, 0.9, "PENDING"));
        var entryPoint = new HttpEntryPoint("GET /orders", "OrderController", "get", "/src/OrderController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph, new LinkRegistry(floatingLinks, List.of()))
            .traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertTrue(result.flows().get(0).steps().stream().anyMatch(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL));
    }

    @Test
    void traceAllRespectsMaxDepth() {
        var edges = List.of(
            CallGraphEdge.resolved("E0", "m", "/src/E0.java", "E1", "m", "/src/E1.java", 0),
            CallGraphEdge.resolved("E1", "m", "/src/E1.java", "E2", "m", "/src/E2.java", 0),
            CallGraphEdge.resolved("E2", "m", "/src/E2.java", "E3", "m", "/src/E3.java", 0),
            CallGraphEdge.resolved("E3", "m", "/src/E3.java", "E4", "m", "/src/E4.java", 0),
            CallGraphEdge.resolved("E4", "m", "/src/E4.java", "E5", "m", "/src/E5.java", 0),
            CallGraphEdge.resolved("E5", "m", "/src/E5.java", "E6", "m", "/src/E6.java", 0));
        var graph = new StructuralGraph(edges, List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("ep", "E0", "m", "/src/E0.java",
            0.5, false, "GET", "/test", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertTrue(result.flows().get(0).steps().size() <= 6);
    }

    @Test
    void traceAllTracksUnresolvedCalls() {
        var edges = List.of(
            CallGraphEdge.unresolved("OrderController", "get", "/src/OrderController.java", "ExternalService", "call", 0));
        var graph = new StructuralGraph(edges, List.of(), List.of(), List.of());
        var entryPoint = new HttpEntryPoint("GET /orders", "OrderController", "get", "/src/OrderController.java",
            0.5, false, "GET", "/orders", List.of(), List.of());

        var result = action(graph).traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(1, result.flows().size());
        assertFalse(result.flows().get(0).unresolvedCalls().isEmpty());
    }

    @Test
    void traceAllFiltersFrameworkCalls() throws IOException {
        Path javaFile = tempDir.resolve("OrderController.java");
        Files.writeString(javaFile, """
            package com.example;
            import org.springframework.jdbc.core.JdbcTemplate;
            public class OrderController {}
            """);
        var frameworkPrefixes = List.of("org.springframework.");
        var edges = List.of(
            CallGraphEdge.unresolved("OrderController", "get", javaFile.toString(), "JdbcTemplate", "execute", 0));
        var graph = new StructuralGraph(edges, List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());
        var entryPoint = new HttpEntryPoint("GET /orders", "OrderController", "get", javaFile.toString(),
            0.5, false, "GET", "/orders", List.of(), List.of());

        var action = new TraceFlowAction(knowledge, null, frameworkPrefixes);
        var result = action.traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(1, result.flows().size());
        assertTrue(result.flows().get(0).unresolvedCalls().isEmpty());
    }
}
