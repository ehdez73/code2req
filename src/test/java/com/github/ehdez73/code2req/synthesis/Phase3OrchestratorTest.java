package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.model.ExecutionFinding;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.FindingType;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TaskStoreSchema;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Phase3OrchestratorTest {

    @TempDir
    Path tempDir;

    private Phase3Orchestrator orchestrator;
    private TaskStore taskStore;
    private ExecutionFindingStore executionFindingStore;
    private FloatingLinkStore floatingLinkStore;
    private TopicLinkStore topicLinkStore;
    private MetricsStore metricsStore;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("p3-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        taskStore = new TaskStore(jdbc);
        executionFindingStore = new ExecutionFindingStore(jdbc);
        floatingLinkStore = new FloatingLinkStore(jdbc);
        topicLinkStore = new TopicLinkStore(jdbc);
        metricsStore = new MetricsStore(jdbc);
        mapper = new ObjectMapper();

        orchestrator = new Phase3Orchestrator(
            taskStore, executionFindingStore, floatingLinkStore,
            topicLinkStore, metricsStore);
    }

    @Test
    void buildCodebaseKnowledgeWithEmptyStore() {
        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertNotNull(knowledge);
        assertNotNull(knowledge.structuralGraph());
        assertNotNull(knowledge.semanticEnrichment());
        assertNotNull(knowledge.linkRegistry());
        assertTrue(knowledge.getFlowCandidates().isEmpty());
        assertTrue(knowledge.getFlowNames().isEmpty());
    }

    @Test
    void buildCodebaseKnowledgeWithCallGraphEdges() throws JsonProcessingException {
        insertTask("task1", "/src/ServiceA.java");
        saveFinding("task1", FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.resolved("src.ServiceA", "doWork", "/src/ServiceA.java",
                "src.ServiceB", "process", "/src/ServiceB.java", 2));
        saveFinding("task1", FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.resolved("src.ServiceA", "validate", "/src/ServiceA.java",
                "src.Validator", "check", "/src/Validator.java", 1));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertEquals(2, knowledge.structuralGraph().callGraphEdges().size());
        List<String> candidates = knowledge.getFlowCandidates();
        assertTrue(candidates.contains("/src/ServiceA.java"));
        assertEquals(1, candidates.size());
    }

    @Test
    void buildCodebaseKnowledgeWithEndpoints() throws JsonProcessingException {
        insertTask("task2", "/src/OrderController.java");
        saveFinding("task2", FindingType.ENDPOINT,
            new EndpointInfo("GET", "/api/orders", "OrderController",
                List.of("id"), List.of(), "/src/OrderController.java", false, null));
        saveFinding("task2", FindingType.ENDPOINT,
            new EndpointInfo("POST", "/api/orders", "OrderController",
                List.of(), List.of(), "/src/OrderController.java", false, null));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertEquals(2, knowledge.structuralGraph().endpoints().size());
        assertEquals(1, knowledge.structuralGraph().getEndpointsByHttpMethod("GET").size());
        assertEquals(1, knowledge.structuralGraph().getEndpointsByHttpMethod("POST").size());
    }

    @Test
    void buildCodebaseKnowledgeWithDbAccess() throws JsonProcessingException {
        insertTask("task3", "/src/OrderRepository.java");
        saveFinding("task3", FindingType.DB_ACCESS,
            new DbAccessInfo("SPRING_DATA", null, "orders", null,
                "findByStatus", "OrderRepository", "/src/OrderRepository.java",
                "Order", false));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertEquals(1, knowledge.structuralGraph().dbAccessPatterns().size());
    }

    @Test
    void buildCodebaseKnowledgeWithComponents() throws JsonProcessingException {
        insertTask("task4", "/src/OrderService.java");
        saveFinding("task4", FindingType.COMPONENT,
            new ComponentInfo("@Service", "OrderService", "com.acme", "/src/OrderService.java"));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertEquals(1, knowledge.structuralGraph().components().size());
        assertEquals(1, knowledge.structuralGraph().getComponentsByType("@Service").size());
    }

    @Test
    void buildCodebaseKnowledgeWithSemanticEnrichment() throws JsonProcessingException {
        insertTask("task5", "/src/PaymentService.java");
        var executionFinding = new ExecutionFinding(
            new ExecutionFinding.Metadata("task5", "test", "/src/PaymentService.java",
                "java-spring", "payments", "2026-01-01T00:00:00Z"),
            new ExecutionFinding.BusinessAbstraction(
                "Processes payments",
                List.of(new ExecutionFinding.HappyPath("Standard payment", "Customer pays with valid card"))),
            new ExecutionFinding.BusinessRulesAndGuardrails(
                List.of(new ExecutionFinding.Validation("amount", "Must be positive", "Throws error")),
                List.of(new ExecutionFinding.EdgeCase("Null amount", "Rejected"))),
            List.of(),
            new ExecutionFinding.ArchitecturalConnections(
                new ExecutionFinding.Inbound(
                    List.of(new ExecutionFinding.HttpEndpoint("POST", "/api/payments", "Process payment")),
                    List.of(), List.of()),
                new ExecutionFinding.Outbound(
                    List.of(new ExecutionFinding.HttpCall("POST", "https://gateway.example.com/charge",
                        "chargeService", true, "REST")),
                    List.of())),
            List.of());
        saveFindingJson("task5", FindingType.SEMANTIC_ENRICHMENT,
            mapper.writeValueAsString(executionFinding));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertEquals(1, knowledge.semanticEnrichment().size());
        assertTrue(knowledge.semanticEnrichment().findByFilePath("/src/PaymentService.java").isPresent());
        assertEquals(List.of("Standard payment"), knowledge.getFlowNames());
    }

    @Test
    void buildCodebaseKnowledgeWithFloatingAndTopicLinks() {
        floatingLinkStore.saveAll(List.of(
            new FloatingLinkInfo("GET", "http://external/api", false, "RestTemplate",
                "src/Client.java", "callExternal", null, 0.0, "PENDING"),
            new FloatingLinkInfo("POST", "/internal/api", false, "WebClient",
                "src/Internal.java", "send", "/target", 1.0, "RESOLVED")));

        topicLinkStore.saveAll(List.of(
            new TopicLink("kafka", "orders-topic", null, "src/Publisher.java",
                null, "src/Consumer.java", "RESOLVED"),
            new TopicLink("rabbitmq", "alerts-queue", null, null,
                null, "src/AlertListener.java", "PENDING")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        LinkRegistry registry = knowledge.linkRegistry();
        assertEquals(2, registry.floatingLinks().size());
        assertEquals(2, registry.topicLinks().size());
        assertEquals(1, registry.findUnresolvedFloatingLinks().size());
        assertEquals(1, registry.findUnresolvedTopicLinks().size());
    }

    @Test
    void buildCodebaseKnowledgeWithCallGraphSupportsQueries() throws JsonProcessingException {
        insertTask("t1", "/src/Controller.java");
        insertTask("t2", "/src/Service.java");
        insertTask("t3", "/src/Repo.java");

        saveFinding("t1", FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.resolved("Controller", "handle", "/src/Controller.java",
                "Service", "execute", "/src/Service.java", 1));
        saveFinding("t2", FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.resolved("Service", "execute", "/src/Service.java",
                "Repo", "find", "/src/Repo.java", 1));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        List<CallGraphEdge> calleesOfController =
            knowledge.getCalleesOf("/src/Controller.java");
        assertEquals(1, calleesOfController.size());
        assertEquals("Service", calleesOfController.getFirst().targetClassName());

        List<CallGraphEdge> callersOfRepo =
            knowledge.getCallersOf("/src/Repo.java");
        assertEquals(1, callersOfRepo.size());
        assertEquals("Service", callersOfRepo.getFirst().sourceClassName());
    }

    @Test
    void analyzeKnowledgeReturnsCorrectCounts() {
        var graph = new StructuralGraph();
        var enrichment = new SemanticEnrichment(java.util.Map.of(
            "/src/A.java", new ExecutionFinding(
                new ExecutionFinding.Metadata("t1", "test", "/src/A.java",
                    "java", "mod", "now"),
                new ExecutionFinding.BusinessAbstraction(
                    "Does A", List.of(
                        new ExecutionFinding.HappyPath("Flow Alpha", "Alpha flow"),
                        new ExecutionFinding.HappyPath("Flow Beta", "Beta flow"))),
                new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
                List.of(),
                new ExecutionFinding.ArchitecturalConnections(
                    new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                    new ExecutionFinding.Outbound(List.of(), List.of())),
                List.of())
        ));
        var links = new LinkRegistry(
            List.of(new FloatingLinkInfo("GET", "http://unknown", false, "RT",
                "src/A.java", "m", null, 0.0, "PENDING")),
            List.of(new TopicLink("kafka", "unknown-topic", null, null,
                null, null, "PENDING")));
        var knowledge = new CodebaseKnowledge(graph, enrichment, links);

        Phase3Result result = Phase3Orchestrator.analyzeKnowledge(knowledge);

        assertEquals(2, result.flowsExtracted());
        assertEquals(2, result.ambiguityGaps());
        assertTrue(result.flowNames().contains("Flow Alpha"));
        assertTrue(result.flowNames().contains("Flow Beta"));
    }

    @Test
    void dryRunReturnsEmptyKnowledge() {
        var status = new com.github.ehdez73.code2req.orchestrator.CompletionStatus(0, 0, 0, 0, 0, 0, List.of());
        Phase3Result result = orchestrator.execute(status, true);

        assertNotNull(result);
    }

    @Test
    void executeWithNoDataReturnsZeroCounts() {
        var status = new com.github.ehdez73.code2req.orchestrator.CompletionStatus(0, 0, 0, 0, 0, 0, List.of());
        Phase3Result result = orchestrator.execute(status, false);

        assertEquals(0, result.flowsExtracted());
        assertEquals(0, result.ambiguityGaps());
        assertEquals(0, result.awaitingReview());
    }

    private void insertTask(String taskId, String filePath) {
        taskStore.save(new Task(taskId, filePath, TaskStatus.ENRICHED, "java", "hash-" + taskId, "test"));
    }

    private void saveFinding(String taskId, String findingType, Object finding)
            throws JsonProcessingException {
        String json = mapper.writeValueAsString(finding);
        executionFindingStore.save(taskId, findingType, json, true);
    }

    private void saveFindingJson(String taskId, String findingType, String json) {
        executionFindingStore.save(taskId, findingType, json, true);
    }
}
