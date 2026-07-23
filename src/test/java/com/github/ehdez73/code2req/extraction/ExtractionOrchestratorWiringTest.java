package com.github.ehdez73.code2req.extraction;

import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.QualifierInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.java.BeanMethodInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlBeanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.ExtractionConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ExtractionOrchestratorWiringTest {

    @TempDir
    Path tempDir;

    private ExtractionOrchestrator orchestrator;
    private ExecutionFindingStore executionFindingStore;
    private TaskStore taskStore;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("wiring-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        taskStore = new TaskStore(jdbc);
        executionFindingStore = new ExecutionFindingStore(jdbc);
        var metricsStore = new com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore(jdbc);
        var floatingLinkStore = new com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore(jdbc);
        var topicLinkStore = new com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore(jdbc);
        mapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        orchestrator = new ExtractionOrchestrator(
            taskStore, executionFindingStore, floatingLinkStore,
            topicLinkStore, metricsStore, mock(AgentPlatform.class),
            new ExtractionConfig(null, null), null);
    }

    private void saveFinding(String findingType, Object finding) throws Exception {
        String json = mapper.writeValueAsString(finding);
        executionFindingStore.save("wiring", findingType, json, true);
    }

    @Test
    void xmlBeanResolvedInWiringMap() throws Exception {
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("nameService", "com.example.RandomNameService", null, null, "/ctx.xml", 5));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "get", "/src/C.java",
                "NameService", "getName", 0,
                List.of("RandomNameService.getName()", "FixedNameService.getName()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertEquals("RandomNameService", wiringMap.get("NameService"),
            "Expected wiring map to resolve NameService → RandomNameService via XML bean");
    }

    @Test
    void xmlBeanPreferredOverComponentInWiringMap() throws Exception {
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("nameService", "com.example.RandomNameService", null, null, "/ctx.xml", 5));

        saveFinding(FindingType.COMPONENT,
            new ComponentInfo("Service", "FixedNameService", "com.example", "/src/FixedNameService.java", false));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "get", "/src/C.java",
                "NameService", "getName", 0,
                List.of("RandomNameService.getName()", "FixedNameService.getName()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertEquals("RandomNameService", wiringMap.get("NameService"),
            "Expected XML bean to win over @Component in wiring resolution");
    }

    @Test
    void primaryBeanTakesPriority() throws Exception {
        saveFinding(FindingType.COMPONENT,
            new ComponentInfo("Service", "StripeGateway", "com.example", "/src/StripeGateway.java", true));

        saveFinding(FindingType.COMPONENT,
            new ComponentInfo("Service", "PaypalGateway", "com.example", "/src/PaypalGateway.java", false));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "pay", "/src/C.java",
                "PaymentGateway", "charge", 0,
                List.of("StripeGateway.charge()", "PaypalGateway.charge()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertEquals("StripeGateway", wiringMap.get("PaymentGateway"),
            "Expected @Primary bean to win");
    }

    @Test
    void qualifierResolvesViaBeanRegistry() throws Exception {
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("stripe", "com.example.StripeGateway", null, null, "/ctx.xml", 3));

        saveFinding(FindingType.QUALIFIER,
            new QualifierInfo("OrderController", "paymentGateway", "stripe", "/src/OrderController.java"));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("OrderController", "pay", "/src/OrderController.java",
                "PaymentGateway", "charge", 0,
                List.of("StripeGateway.charge()", "PaypalGateway.charge()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertEquals("StripeGateway", wiringMap.get("PaymentGateway"),
            "Expected @Qualifier \"stripe\" to resolve via XML bean registry");
    }

    @Test
    void qualifierConventionMatchResolves() throws Exception {
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("externalNameService", "com.example.ExternalNameService", null, null, "/ctx.xml", 3));

        saveFinding(FindingType.QUALIFIER,
            new QualifierInfo("OrderController", "nameService", "externalNameService", "/src/OrderController.java"));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("OrderController", "get", "/src/OrderController.java",
                "NameService", "getName", 0,
                List.of("RandomNameService.getName()", "ExternalNameService.getName()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertEquals("ExternalNameService", wiringMap.get("NameService"),
            "Expected @Qualifier \"externalNameService\" to match ExternalNameService via convention");
    }

    @Test
    void noAmbiguousEdgesMeansEmptyWiringMap() throws Exception {
        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.resolved("Controller", "get", "/src/C.java",
                "Service", "process", "/src/S.java", 0));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertTrue(knowledge.structuralGraph().wiringMap().isEmpty());
    }

    @Test
    void classToFileMapBuiltFromEdges() throws Exception {
        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "get", "/src/C.java",
                "NameService", "getName", 0,
                List.of("RandomNameService.getName()", "FixedNameService.getName()")));

        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("nameService", "com.example.RandomNameService", null, null, "/ctx.xml", 5));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertFalse(wiringMap.isEmpty(), "Expected wiring map to be non-empty");
        assertNotNull(wiringMap.get("NameService"));
    }

    @Test
    void primaryBeanWinsOverXmlAndQualifier() throws Exception {
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("nameService", "com.example.RandomNameService", null, null, "/ctx.xml", 5));

        saveFinding(FindingType.COMPONENT,
            new ComponentInfo("Service", "RandomNameService", "com.example", "/src/RandomNameService.java", false));

        saveFinding(FindingType.COMPONENT,
            new ComponentInfo("Service", "FixedNameService", "com.example", "/src/FixedNameService.java", true));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "get", "/src/C.java",
                "NameService", "getName", 0,
                List.of("RandomNameService.getName()", "FixedNameService.getName()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertEquals("FixedNameService", wiringMap.get("NameService"),
            "Expected @Primary to win over XML bean");
    }

    @Test
    void multipleAmbiguousEdgesAllResolvedInWiringMap() throws Exception {
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("svc", "com.example.ServiceImpl", null, null, "/ctx.xml", 5));
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("repo", "com.example.RepoImpl", null, null, "/ctx.xml", 6));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "get", "/src/C.java",
                "MyService", "process", 0,
                List.of("ServiceImpl.process()", "ServiceMock.process()")));
        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "load", "/src/C.java",
                "MyRepo", "find", 0,
                List.of("RepoMock.find()", "RepoImpl.find()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        Map<String, String> wiringMap = knowledge.structuralGraph().wiringMap();
        assertEquals("ServiceImpl", wiringMap.get("MyService"));
        assertEquals("RepoImpl", wiringMap.get("MyRepo"));
    }

    @Test
    void noDoubleBeanCount_WhenBothXmlAndComponent() throws Exception {
        saveFinding(FindingType.XML_BEAN,
            new XmlBeanInfo("svc", "com.example.ServiceImpl", null, null, "/ctx.xml", 5));
        saveFinding(FindingType.COMPONENT,
            new ComponentInfo("Service", "ServiceImpl", "com.example", "/src/ServiceImpl.java", false));

        saveFinding(FindingType.CALL_GRAPH_EDGE,
            CallGraphEdge.ambiguous("Controller", "get", "/src/C.java",
                "MyService", "process", 0,
                List.of("ServiceImpl.process()", "ServiceMock.process()")));

        CodebaseKnowledge knowledge = orchestrator.buildCodebaseKnowledge();

        assertEquals("ServiceImpl", knowledge.structuralGraph().wiringMap().get("MyService"));
    }
}
