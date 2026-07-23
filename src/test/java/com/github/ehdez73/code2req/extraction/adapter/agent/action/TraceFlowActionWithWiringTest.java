package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.EntryPointDiscoveryResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TraceFlowActionWithWiringTest {

    @TempDir
    Path tempDir;

    @Test
    void wiringMapResolvesAmbiguousEdge() throws IOException {
        Path implFile = tempDir.resolve("PaypalGateway.java");
        Files.writeString(implFile, "public class PaypalGateway {}");

        var edge = CallGraphEdge.ambiguous("OrderController", "pay", "/src/OrderController.java",
            "PaymentGateway", "charge", 0, List.of("PaypalGateway.charge()", "StripeGateway.charge()"));

        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            Map.of("PaymentGateway", "PaypalGateway"),
            Map.of("PaypalGateway", implFile.toString()));

        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());
        var action = new TraceFlowAction(knowledge, null);

        var entryPoint = new HttpEntryPoint("POST /pay", "OrderController", "pay", "/src/OrderController.java",
            0.5, false, "POST", "/pay", List.of(), List.of());

        var result = action.traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(1, result.flows().size());
        var flow = result.flows().get(0);
        assertTrue(flow.unresolvedCalls().isEmpty(),
            "Expected no unresolved calls after wiring resolution, got: " + flow.unresolvedCalls());
        assertEquals(2, flow.steps().size(), "Expected entry point + resolved target step");
        assertEquals("PaymentGateway", flow.steps().get(1).className(),
            "Expected resolved interface name in step");
        assertEquals(implFile.toString(), flow.steps().get(1).sourceFile(),
            "Expected concrete implementation file path in step");
    }

    @Test
    void ambiguousEdgeWithoutWiringGetsUnresolvedCall() {
        var edge = CallGraphEdge.ambiguous("OrderController", "pay", "/src/OrderController.java",
            "PaymentGateway", "charge", 0, List.of("PaypalGateway.charge()", "StripeGateway.charge()"));

        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());
        var action = new TraceFlowAction(knowledge, null);

        var entryPoint = new HttpEntryPoint("POST /pay", "OrderController", "pay", "/src/OrderController.java",
            0.5, false, "POST", "/pay", List.of(), List.of());

        var result = action.traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(1, result.flows().size());
        assertFalse(result.flows().get(0).unresolvedCalls().isEmpty());
    }

    @Test
    void unresolvedEdgeWithoutWiringGetsUnresolvedCall() {
        var edge = CallGraphEdge.unresolved("OrderController", "pay", "/src/OrderController.java",
            "ExternalService", "call", 0);

        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());
        var action = new TraceFlowAction(knowledge, null);

        var entryPoint = new HttpEntryPoint("POST /pay", "OrderController", "pay", "/src/OrderController.java",
            0.5, false, "POST", "/pay", List.of(), List.of());

        var result = action.traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(1, result.flows().size());
        assertFalse(result.flows().get(0).unresolvedCalls().isEmpty());
    }

    @Test
    void wiringMapWithMissingClassFileLeavesUnresolved() {
        var edge = CallGraphEdge.ambiguous("OrderController", "pay", "/src/OrderController.java",
            "PaymentGateway", "charge", 0, List.of("PaypalGateway.charge()", "StripeGateway.charge()"));

        var graph = new StructuralGraph(List.of(edge), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            Map.of("PaymentGateway", "PaypalGateway"),
            Map.of()); // empty classToFileMap — file not found

        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());
        var action = new TraceFlowAction(knowledge, null);

        var entryPoint = new HttpEntryPoint("POST /pay", "OrderController", "pay", "/src/OrderController.java",
            0.5, false, "POST", "/pay", List.of(), List.of());

        var result = action.traceAll(new EntryPointDiscoveryResult(List.of(entryPoint), List.of()));

        assertEquals(1, result.flows().size());
        assertFalse(result.flows().get(0).unresolvedCalls().isEmpty());
    }
}
