package com.github.ehdez73.code2req.extraction;

import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodebaseKnowledgeTest {

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

    @Test
    void constructorStoresSubComponents() {
        var graph = new StructuralGraph();
        var enrichment = new SemanticEnrichment();
        var links = new LinkRegistry();

        var knowledge = new CodebaseKnowledge(graph, enrichment, links);

        assertSame(graph, knowledge.structuralGraph());
        assertSame(enrichment, knowledge.semanticEnrichment());
        assertSame(links, knowledge.linkRegistry());
    }

    @Test
    void getFlowCandidatesDelegatesToStructuralGraph() {
        var graph = new StructuralGraph(
            List.of(CallGraphEdge.resolved("A", "m", "/src/A.java", "B", "n", "/src/B.java", 0)),
            List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        assertEquals(1, knowledge.getFlowCandidates().size());
        assertTrue(knowledge.getFlowCandidates().contains("/src/A.java"));
    }

    @Test
    void getComponentsByTypeDelegatesToStructuralGraph() {
        var graph = new StructuralGraph(
            List.of(), List.of(), List.of(),
            List.of(
                new ComponentInfo("@Service", "S1", "p", "/src/S1.java"),
                new ComponentInfo("@Repository", "R1", "p", "/src/R1.java")));
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        assertEquals(1, knowledge.getComponentsByType("@Service").size());
        assertEquals(1, knowledge.getComponentsByType("@Repository").size());
    }

    @Test
    void getCallersOfDelegatesToStructuralGraph() {
        var graph = new StructuralGraph(
            List.of(
                CallGraphEdge.resolved("A", "m", "/src/A.java", "T", "n", "/src/T.java", 0),
                CallGraphEdge.resolved("B", "m", "/src/B.java", "T", "n", "/src/T.java", 0)),
            List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var callers = knowledge.getCallersOf("/src/T.java");
        assertEquals(2, callers.size());
    }

    @Test
    void getCalleesOfDelegatesToStructuralGraph() {
        var graph = new StructuralGraph(
            List.of(
                CallGraphEdge.resolved("A", "m1", "/src/A.java", "B", "n1", "/src/B.java", 0),
                CallGraphEdge.resolved("A", "m2", "/src/A.java", "C", "n2", "/src/C.java", 0)),
            List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var callees = knowledge.getCalleesOf("/src/A.java");
        assertEquals(2, callees.size());
    }

    @Test
    void getAllHappyPathsDelegatesToSemanticEnrichment() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "Flow A"),
            "/src/B.java", finding("/src/B.java", "Flow B")));
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), enrichment, new LinkRegistry());

        var paths = knowledge.getAllHappyPaths();
        assertEquals(2, paths.size());
    }

    @Test
    void getFlowNamesDelegatesToSemanticEnrichment() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", finding("/src/A.java", "Flow X")));
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), enrichment, new LinkRegistry());

        assertEquals(List.of("Flow X"), knowledge.getFlowNames());
    }

    @Test
    void findUnresolvedLinksDelegatesToLinkRegistry() {
        var links = new LinkRegistry(
            List.of(new FloatingLinkInfo("GET", "http://unknown", false, "RT",
                "src/A.java", "m", null, 0.0, "PENDING")),
            List.of(TopicLink.orphanProducer("kafka", "orders", "P1", "src/P1.java")));
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), links);

        assertEquals(1, knowledge.findUnresolvedLinks().size());
        assertEquals(1, knowledge.findUnresolvedTopicLinks().size());
    }

    @Test
    void findAllFloatingLinksDelegatesToLinkRegistry() {
        var links = new LinkRegistry(
            List.of(new FloatingLinkInfo("GET", "http://a.com", false, "RT",
                "src/A.java", "m", "/target", 1.0, "RESOLVED")),
            List.of());
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), links);

        assertEquals(1, knowledge.findAllFloatingLinks().size());
    }

    @Test
    void findAllTopicLinksDelegatesToLinkRegistry() {
        var links = new LinkRegistry(
            List.of(),
            List.of(TopicLink.resolved("kafka", "t", "P", "/src/P.java", "C", "/src/C.java")));
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), links);

        assertEquals(1, knowledge.findAllTopicLinks().size());
    }

    @Test
    void getEntryPointsDelegatesToStructuralGraph() {
        var graph = new StructuralGraph(
            List.of(), List.of(
                new EndpointInfo("GET", "/api", "Ctrl", List.of(), List.of(), "/src/Ctrl.java", false, null)),
            List.of(), List.of(),
            List.of(new ScheduledTaskInfo("run", "Task", "0 * * * *", null, null, "cron", "/src/Task.java")),
            List.of(), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var entryPoints = knowledge.getEntryPoints();

        assertEquals(2, entryPoints.size());
        assertTrue(entryPoints.stream().anyMatch(ep -> ep.type() == EntryPointType.HTTP));
        assertTrue(entryPoints.stream().anyMatch(ep -> ep.type() == EntryPointType.SCHEDULED));
    }

    @Test
    void getAllKnownMethodsDelegatesToStructuralGraph() {
        var graph = new StructuralGraph(
            List.of(), List.of(), List.of(), List.of(),
            List.of(new ScheduledTaskInfo("run", "Task", "0 * * * *", null, null, "cron", "/src/Task.java")),
            List.of(), List.of(), List.of(), List.of());
        var knowledge = new CodebaseKnowledge(graph, new SemanticEnrichment(), new LinkRegistry());

        var methods = knowledge.getAllKnownMethods();
        assertEquals(1, methods.size());
        assertEquals("run", methods.get(0).methodName());
    }

    @Test
    void getAllTestInsightsDelegatesToSemanticEnrichment() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "F1", "/src/ATest.java")));
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), enrichment, new LinkRegistry());

        var insights = knowledge.getAllTestInsights();
        assertEquals(1, insights.size());
        assertEquals("/src/ATest.java", insights.get(0).testFilePath());
    }

    @Test
    void getAllTestFilePathsDelegatesToSemanticEnrichment() {
        var enrichment = new SemanticEnrichment(Map.of(
            "/src/A.java", findingWithTestInsight("/src/A.java", "F1", "/src/ATest.java"),
            "/src/B.java", finding("/src/B.java", "F2")));
        var knowledge = new CodebaseKnowledge(new StructuralGraph(), enrichment, new LinkRegistry());

        var mapping = knowledge.getAllTestFilePaths();
        assertEquals(1, mapping.size());
        assertEquals("/src/ATest.java", mapping.get("/src/A.java"));
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
}
