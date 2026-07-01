package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.GroupedFlowsResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.ComplexityLevel;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.KafkaEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationshipType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrossReferenceFlowsActionTest {

    private final EntryPoint httpEntry = new HttpEntryPoint("GET /orders", "OrderController", "get", "/src/OrderController.java",
        0.5, false, "GET", "/orders", List.of(), List.of());

    private final EntryPoint schedEntry = new ScheduledEntryPoint("scheduled-task", "OrderScheduler", "process", "/src/OrderScheduler.java",
        0.5, false, "0 * * * *");

    private final FlowStep step = new FlowStep(0, FlowStepComponentType.SERVICE, "OrderService", "process",
        null, "/src/OrderService.java", 0, 0, List.of());

    @Test
    void crossReferenceWithNoLinksReturnsNoRelationships() {
        var graph = new StructuralGraph();
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());
        var action = new CrossReferenceFlowsAction(knowledge);
        var flow = new FunctionalFlow("flow-1", "GET /orders", httpEntry, List.of(step),
            null, List.of(), List.of(), List.of(), ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Orders", "Orders feature", List.of(flow), List.of());
        var grouped = new GroupedFlowsResult(List.of(feature));

        var result = action.crossReference(grouped);

        assertTrue(result.crossFlowRelationships().isEmpty());
        assertEquals(1, result.features().size());
    }

    @Test
    void crossReferenceHttpLinkCreatesRelationship() {
        var endpoints = List.of(
            new EndpointInfo("GET", "/api/payment", "PaymentController", "", List.of(), List.of(), "/src/PaymentController.java", false, null, List.of()));
        var graph = new StructuralGraph(List.of(), endpoints, List.of(), List.of());
        var floatingLinks = List.of(
            new FloatingLinkInfo("POST", "/api/payment", false, "RestTemplate", "/src/OrderService.java", "process", null, 0.9, "PENDING"));
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry(floatingLinks, List.of()));
        var action = new CrossReferenceFlowsAction(knowledge);

        var targetEp = new HttpEntryPoint("GET /api/payment", "PaymentController", "handle", "/src/PaymentController.java",
            0.5, false, "GET", "/api/payment", List.of(), List.of());
        var targetFlow = new FunctionalFlow("flow-2", "GET /api/payment", targetEp, List.of(step),
            null, List.of(), List.of(), List.of(), ComplexityLevel.MINIMAL, null);
        var sourceFlow = new FunctionalFlow("flow-1", "GET /orders", httpEntry, List.of(
            new FlowStep(0, FlowStepComponentType.SERVICE, "OrderService", "process", null, "/src/OrderService.java", 0, 0, List.of())),
            null, List.of(), List.of(), List.of(), ComplexityLevel.MINIMAL, null);

        var feature = new FunctionalFeature("feature-1", "Orders", "Orders feature", List.of(sourceFlow, targetFlow), List.of());
        var grouped = new GroupedFlowsResult(List.of(feature));

        var result = action.crossReference(grouped);

        assertFalse(result.crossFlowRelationships().isEmpty());
        assertEquals(FlowRelationshipType.CALLS_EXTERNAL, result.crossFlowRelationships().get(0).type());
    }

    @Test
    void crossReferenceTopicLinkCreatesRelationship() {
        var graph = new StructuralGraph();
        var topicLinks = List.of(
            TopicLink.orphanConsumer("KAFKA", "orders-topic", "OrderListener", "/src/OrderListener.java"));
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry(List.of(), topicLinks));
        var action = new CrossReferenceFlowsAction(knowledge);

        var consumerEp = new KafkaEntryPoint("orders-topic", "OrderListener", "onMessage", "/src/OrderListener.java",
            0.5, false, "orders-topic", false, "");
        var consumerFlow = new FunctionalFlow("flow-2", "Consume orders", consumerEp, List.of(step),
            null, List.of(), List.of(), List.of(), ComplexityLevel.MINIMAL, null);
        var producerFlow = new FunctionalFlow("flow-1", "GET /orders", httpEntry, List.of(
            new FlowStep(0, FlowStepComponentType.SERVICE, "OrderService", "process", null, "/src/OrderService.java", 0, 0,
                List.of("orders-topic"))),
            null, List.of(), List.of(), List.of(), ComplexityLevel.MINIMAL, null);

        var feature = new FunctionalFeature("feature-1", "Orders", "Orders feature", List.of(producerFlow, consumerFlow), List.of());
        var grouped = new GroupedFlowsResult(List.of(feature));

        var result = action.crossReference(grouped);

        assertFalse(result.crossFlowRelationships().isEmpty());
        assertTrue(result.crossFlowRelationships().stream()
            .allMatch(r -> r.type() == FlowRelationshipType.PUBLISHES_EVENT || r.type() == FlowRelationshipType.CONSUMES_EVENT));
    }

    @Test
    void crossReferenceSkipsSelfRelationships() {
        var endpoints = List.of(
            new EndpointInfo("GET", "/api/orders", "OrderController", "", List.of(), List.of(), "/src/OrderController.java", false, null, List.of()));
        var graph = new StructuralGraph(List.of(), endpoints, List.of(), List.of());
        var floatingLinks = List.of(
            new FloatingLinkInfo("GET", "/api/orders", false, "RestTemplate", "/src/OrderService.java", "process", null, 0.9, "PENDING"));
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry(floatingLinks, List.of()));
        var action = new CrossReferenceFlowsAction(knowledge);

        var flow = new FunctionalFlow("flow-1", "GET /api/orders", httpEntry, List.of(step),
            null, List.of(), List.of(), List.of(), ComplexityLevel.MINIMAL, null);
        var feature = new FunctionalFeature("feature-1", "Orders", "Orders feature", List.of(flow), List.of());
        var grouped = new GroupedFlowsResult(List.of(feature));

        var result = action.crossReference(grouped);

        assertTrue(result.crossFlowRelationships().isEmpty());
    }

    @Test
    void crossReferenceWithEmptyGroupedResultReturnsEmpty() {
        var graph = new StructuralGraph();
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var result = new CrossReferenceFlowsAction(knowledge).crossReference(new GroupedFlowsResult(List.of()));

        assertTrue(result.features().isEmpty());
        assertTrue(result.crossFlowRelationships().isEmpty());
    }
}
