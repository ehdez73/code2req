package com.github.ehdez73.code2req.extraction;

import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.EventListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.MethodCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuralGraphTest {

    @Test
    void emptyConstructorCreatesEmptyGraph() {
        var graph = new StructuralGraph();
        assertTrue(graph.callGraphEdges().isEmpty());
        assertTrue(graph.endpoints().isEmpty());
        assertTrue(graph.dbAccessPatterns().isEmpty());
        assertTrue(graph.components().isEmpty());
        assertTrue(graph.scheduledTasks().isEmpty());
        assertTrue(graph.kafkaListeners().isEmpty());
        assertTrue(graph.rabbitmqListeners().isEmpty());
        assertTrue(graph.activemqListeners().isEmpty());
        assertTrue(graph.eventListeners().isEmpty());
    }

    @Test
    void fourArgConstructorDelegatesToNineArg() {
        var edges = List.of(
            CallGraphEdge.resolved("A", "m1", "/src/A.java", "B", "m2", "/src/B.java", 1));
        var endpoints = List.of(
            new EndpointInfo("GET", "/api", "C", List.of(), List.of(), "/src/C.java", false, null, List.of()));
        var dbAccess = List.of(
            new DbAccessInfo("JPA", null, "table", null, "find", "D", "/src/D.java", "E", false));
        var components = List.of(
            new ComponentInfo("@Service", "E", "com.acme", "/src/E.java"));

        var graph = new StructuralGraph(edges, endpoints, dbAccess, components);

        assertEquals(1, graph.callGraphEdges().size());
        assertEquals(1, graph.endpoints().size());
        assertEquals(1, graph.dbAccessPatterns().size());
        assertEquals(1, graph.components().size());
        assertTrue(graph.scheduledTasks().isEmpty());
        assertTrue(graph.kafkaListeners().isEmpty());
        assertTrue(graph.rabbitmqListeners().isEmpty());
        assertTrue(graph.activemqListeners().isEmpty());
        assertTrue(graph.eventListeners().isEmpty());
    }

    @Test
    void nineArgConstructorStoresAllFields() {
        var edges = List.of(
            CallGraphEdge.resolved("A", "m", "/src/A.java", "B", "n", "/src/B.java", 1));
        var endpoints = List.of(
            new EndpointInfo("GET", "/api", "C", List.of(), List.of(), "/src/C.java", false, null, List.of()));
        var dbAccess = List.of(
            new DbAccessInfo("JPA", null, "t", null, "f", "D", "/src/D.java", "E", false));
        var components = List.of(
            new ComponentInfo("@Service", "F", "p", "/src/F.java"));
        var scheduled = List.of(
            new ScheduledTaskInfo("run", "G", "0 * * * *", null, null, "cron", "/src/G.java"));
        var kafka = List.of(
            new KafkaInfo("topic", "listen", "H", "/src/H.java", false, ""));
        var rabbit = List.of(
            new RabbitMqInfo("queue", "handle", "I", "/src/I.java", ""));
        var activeMq = List.of(
            new ActiveMqInfo("dest", "onMsg", "J", "/src/J.java", ""));
        var events = List.of(
            new EventListenerInfo("AppEvent", "onEvent", "K", "/src/K.java", List.of()));

        var graph = new StructuralGraph(edges, endpoints, dbAccess, components,
            scheduled, kafka, rabbit, activeMq, events);

        assertEquals(1, graph.callGraphEdges().size());
        assertEquals(1, graph.endpoints().size());
        assertEquals(1, graph.dbAccessPatterns().size());
        assertEquals(1, graph.components().size());
        assertEquals(1, graph.scheduledTasks().size());
        assertEquals(1, graph.kafkaListeners().size());
        assertEquals(1, graph.rabbitmqListeners().size());
        assertEquals(1, graph.activemqListeners().size());
        assertEquals(1, graph.eventListeners().size());
    }

    @Test
    void getFlowCandidatesReturnsResolvedEdgesOnly() {
        var graph = new StructuralGraph(
            List.of(
                CallGraphEdge.resolved("A", "m", "/src/A.java", "B", "n", "/src/B.java", 0),
                CallGraphEdge.unresolved("A", "m", "/src/A.java", "C", "n", 0),
                CallGraphEdge.ambiguous("A", "m", "/src/A.java", "D", "n", 0, List.of("D1", "D2"))),
            List.of(), List.of(), List.of());

        var candidates = graph.getFlowCandidates();

        assertEquals(1, candidates.size());
        assertTrue(candidates.contains("/src/A.java"));
    }

    @Test
    void getFlowCandidatesReturnsDistinctPaths() {
        var graph = new StructuralGraph(
            List.of(
                CallGraphEdge.resolved("A", "m1", "/src/A.java", "B", "n1", "/src/B.java", 0),
                CallGraphEdge.resolved("A", "m2", "/src/A.java", "C", "n2", "/src/C.java", 0)),
            List.of(), List.of(), List.of());

        var candidates = graph.getFlowCandidates();

        assertEquals(1, candidates.size());
    }

    @Test
    void getFlowCandidatesWithNoEdgesReturnsEmpty() {
        var graph = new StructuralGraph();
        assertTrue(graph.getFlowCandidates().isEmpty());
    }

    @Test
    void getEndpointsByHttpMethodIsCaseInsensitive() {
        var graph = new StructuralGraph(
            List.of(),
            List.of(
                new EndpointInfo("GET", "/api/a", "C1", List.of(), List.of(), "/src/C1.java", false, null, List.of()),
                new EndpointInfo("get", "/api/b", "C2", List.of(), List.of(), "/src/C2.java", false, null, List.of()),
                new EndpointInfo("POST", "/api/c", "C3", List.of(), List.of(), "/src/C3.java", false, null, List.of())),
            List.of(), List.of());

        assertEquals(2, graph.getEndpointsByHttpMethod("GET").size());
        assertEquals(2, graph.getEndpointsByHttpMethod("get").size());
        assertEquals(1, graph.getEndpointsByHttpMethod("POST").size());
        assertEquals(0, graph.getEndpointsByHttpMethod("PUT").size());
    }

    @Test
    void getCallersOfReturnsEdgesTargetingFile() {
        var graph = new StructuralGraph(
            List.of(
                CallGraphEdge.resolved("A", "m1", "/src/A.java", "T", "n1", "/src/T.java", 0),
                CallGraphEdge.resolved("B", "m2", "/src/B.java", "T", "n2", "/src/T.java", 0),
                CallGraphEdge.resolved("A", "m3", "/src/A.java", "C", "n3", "/src/C.java", 0)),
            List.of(), List.of(), List.of());

        var callers = graph.getCallersOf("/src/T.java");

        assertEquals(2, callers.size());
        assertTrue(callers.stream().allMatch(e -> "/src/T.java".equals(e.targetFilePath())));
    }

    @Test
    void getCalleesOfReturnsEdgesFromSource() {
        var graph = new StructuralGraph(
            List.of(
                CallGraphEdge.resolved("A", "m1", "/src/A.java", "B", "n1", "/src/B.java", 0),
                CallGraphEdge.resolved("A", "m2", "/src/A.java", "C", "n2", "/src/C.java", 0)),
            List.of(), List.of(), List.of());

        var callees = graph.getCalleesOf("/src/A.java");

        assertEquals(2, callees.size());
        assertTrue(callees.stream().allMatch(e -> "/src/A.java".equals(e.sourceFilePath())));
    }

    @Test
    void getComponentsByTypeFiltersByAnnotation() {
        var graph = new StructuralGraph(
            List.of(), List.of(), List.of(),
            List.of(
                new ComponentInfo("@Service", "S1", "p", "/src/S1.java"),
                new ComponentInfo("@Service", "S2", "p", "/src/S2.java"),
                new ComponentInfo("@Repository", "R1", "p", "/src/R1.java")));

        assertEquals(2, graph.getComponentsByType("@Service").size());
        assertEquals(1, graph.getComponentsByType("@Repository").size());
        assertEquals(0, graph.getComponentsByType("@Controller").size());
    }

    @Test
    void getEntryPointsIncludesAllSixTypes() {
        var graph = new StructuralGraph(
            List.of(), List.of(
                new EndpointInfo("GET", "/orders", "OrderCtrl", List.of(), List.of(), "/src/OrderCtrl.java", false, null, List.of())),
            List.of(), List.of(),
            List.of(new ScheduledTaskInfo("process", "Scheduler", "0 * * * *", null, null, "cron", "/src/Scheduler.java")),
            List.of(new KafkaInfo("events", "onEvent", "Listener", "/src/Listener.java", false, "")),
            List.of(new RabbitMqInfo("alerts", "handleAlert", "AlertListener", "/src/AlertListener.java", "")),
            List.of(new ActiveMqInfo("queue.dlq", "onDlq", "DlqHandler", "/src/DlqHandler.java", "")),
            List.of(new EventListenerInfo("MyEvent", "onMyEvent", "EventHandler", "/src/EventHandler.java", List.of())));

        var entryPoints = graph.getEntryPoints();

        assertEquals(6, entryPoints.size());
        assertEquals(1, entryPoints.stream().filter(ep -> ep.type() == EntryPointType.HTTP).count());
        assertEquals(1, entryPoints.stream().filter(ep -> ep.type() == EntryPointType.SCHEDULED).count());
        assertEquals(1, entryPoints.stream().filter(ep -> ep.type() == EntryPointType.KAFKA).count());
        assertEquals(1, entryPoints.stream().filter(ep -> ep.type() == EntryPointType.RABBITMQ).count());
        assertEquals(1, entryPoints.stream().filter(ep -> ep.type() == EntryPointType.ACTIVEMQ).count());
        assertEquals(1, entryPoints.stream().filter(ep -> ep.type() == EntryPointType.EVENT_LISTENER).count());
    }

    @Test
    void getEntryPointsReturnsCorrectIdentifiers() {
        var graph = new StructuralGraph(
            List.of(), List.of(
                new EndpointInfo("POST", "/api/orders", "Ctrl", List.of(), List.of(), "/src/Ctrl.java", false, null, List.of())),
            List.of(), List.of(),
            List.of(new ScheduledTaskInfo("run", "Task", null, 5000L, null, "fixedRate", "/src/Task.java")),
            List.of(), List.of(), List.of(), List.of());

        var entryPoints = graph.getEntryPoints();

        assertEquals("POST /api/orders", entryPoints.get(0).id());
        assertEquals("fixedRate=5000", entryPoints.get(1).id());
    }

    @Test
    void getEntryPointsWithEmptyListsReturnsEmpty() {
        var graph = new StructuralGraph();
        assertTrue(graph.getEntryPoints().isEmpty());
    }

    @Test
    void getAllKnownMethodsReturnsAllListenerMethods() {
        var graph = new StructuralGraph(
            List.of(), List.of(), List.of(), List.of(),
            List.of(new ScheduledTaskInfo("run", "Task1", "0 * * * *", null, null, "cron", "/src/Task1.java")),
            List.of(new KafkaInfo("topic", "consume", "Task2", "/src/Task2.java", false, "")),
            List.of(new RabbitMqInfo("queue", "receive", "Task3", "/src/Task3.java", "")),
            List.of(new ActiveMqInfo("dest", "onMsg", "Task4", "/src/Task4.java", "")),
            List.of(new EventListenerInfo("Event", "handle", "Task5", "/src/Task5.java", List.of())));

        var methods = graph.getAllKnownMethods();

        assertEquals(5, methods.size());
        assertTrue(methods.stream().anyMatch(m -> m.className().equals("Task1") && m.methodName().equals("run")));
        assertTrue(methods.stream().anyMatch(m -> m.className().equals("Task2") && m.methodName().equals("consume")));
        assertTrue(methods.stream().anyMatch(m -> m.className().equals("Task3") && m.methodName().equals("receive")));
        assertTrue(methods.stream().anyMatch(m -> m.className().equals("Task4") && m.methodName().equals("onMsg")));
        assertTrue(methods.stream().anyMatch(m -> m.className().equals("Task5") && m.methodName().equals("handle")));
    }

    @Test
    void getAllKnownMethodsWithEmptyListsReturnsEmpty() {
        var graph = new StructuralGraph();
        assertTrue(graph.getAllKnownMethods().isEmpty());
    }

    @Test
    void getEntryPointPriorityEnrichmentWeight() {
        var graph = new StructuralGraph(
            List.of(CallGraphEdge.resolved("A", "m", "/src/A.java", "B", "n", "/src/B.java", 1)),
            List.of(new EndpointInfo("GET", "/api", "C", List.of(), List.of(), "/src/C.java", false, null, List.of())),
            List.of(), List.of());
        var enrichment = new SemanticEnrichment(Map.of("/src/C.java",
            new ExecutionFinding(
                new ExecutionFinding.Metadata("t1", "test", "/src/C.java", "java", "mod", "now"),
                new ExecutionFinding.BusinessAbstraction("Purpose",
                    List.of(new ExecutionFinding.HappyPath("Flow", "Desc"))),
                new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
                List.of(),
                new ExecutionFinding.ArchitecturalConnections(
                    new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                    new ExecutionFinding.Outbound(List.of(), List.of())),
                List.of())));

        var ep = graph.getEntryPoints().get(0);
        double priority = graph.getEntryPointPriority(ep, enrichment, Map.of());

        assertEquals(0.5, priority, 1e-9);
    }

    @Test
    void getEntryPointPriorityDownstreamCountCaps() {
        var edges = List.of(
            CallGraphEdge.resolved("A", "m", "/src/A.java", "B1", "n", "/src/B1.java", 1),
            CallGraphEdge.resolved("A", "m", "/src/A.java", "B2", "n", "/src/B2.java", 1));
        var graph = new StructuralGraph(edges,
            List.of(new EndpointInfo("GET", "/api", "A", List.of(), List.of(), "/src/A.java", false, null, List.of())),
            List.of(), List.of());
        var enrichment = new SemanticEnrichment(Map.of());

        var ep = graph.getEntryPoints().get(0);
        double priority = graph.getEntryPointPriority(ep, enrichment, Map.of());

        assertEquals(0.26, priority, 1e-9);
    }

    @Test
    void getEntryPointPriorityHttpVsNonHttp() {
        var graph = new StructuralGraph(
            List.of(), List.of(
                new EndpointInfo("GET", "/api", "C", List.of(), List.of(), "/src/C.java", false, null, List.of())),
            List.of(), List.of(),
            List.of(new ScheduledTaskInfo("run", "S", "0 * * * *", null, null, "cron", "/src/S.java")),
            List.of(), List.of(), List.of(), List.of());
        var enrichment = new SemanticEnrichment(Map.of());
        var testMapping = Map.of("/src/C.java", "/src/CTest.java");

        var httpEp = graph.getEntryPoints().stream().filter(ep -> ep.type() == EntryPointType.HTTP).findFirst().get();
        var schedEp = graph.getEntryPoints().stream().filter(ep -> ep.type() == EntryPointType.SCHEDULED).findFirst().get();

        double httpPriority = graph.getEntryPointPriority(httpEp, enrichment, testMapping);
        double schedPriority = graph.getEntryPointPriority(schedEp, enrichment, testMapping);

        assertTrue(httpPriority > schedPriority);
    }

    @Test
    void getEntryPointPriorityTestFileWeight() {
        var graph = new StructuralGraph(
            List.of(), List.of(
                new EndpointInfo("GET", "/api", "C", List.of(), List.of(), "/src/C.java", false, null, List.of())),
            List.of(), List.of());
        var enrichment = new SemanticEnrichment(Map.of());

        var ep = graph.getEntryPoints().get(0);
        double withTest = graph.getEntryPointPriority(ep, enrichment, Map.of("/src/C.java", "/src/CTest.java"));
        double withoutTest = graph.getEntryPointPriority(ep, enrichment, Map.of());

        assertEquals(0.2, withTest - withoutTest, 1e-9);
    }

    @Test
    void listsAreUnmodifiable() {
        var graph = new StructuralGraph(
            List.of(CallGraphEdge.resolved("A", "m", "/src/A.java", "B", "n", "/src/B.java", 1)),
            List.of(), List.of(), List.of());

        assertThrows(UnsupportedOperationException.class, () -> graph.callGraphEdges().add(null));
        assertThrows(UnsupportedOperationException.class, () -> graph.endpoints().add(null));
    }
}
