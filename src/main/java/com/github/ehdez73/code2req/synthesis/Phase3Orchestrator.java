package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.model.ExecutionFinding;
import com.github.ehdez73.code2req.model.Metric;
import com.github.ehdez73.code2req.orchestrator.CompletionStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FindingType;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class Phase3Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Phase3Orchestrator.class);

    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final FloatingLinkStore floatingLinkStore;
    private final TopicLinkStore topicLinkStore;
    private final MetricsStore metricsStore;
    private final ObjectMapper objectMapper;

    public Phase3Orchestrator(
            TaskStore taskStore,
            ExecutionFindingStore executionFindingStore,
            FloatingLinkStore floatingLinkStore,
            TopicLinkStore topicLinkStore,
            MetricsStore metricsStore) {
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.floatingLinkStore = floatingLinkStore;
        this.topicLinkStore = topicLinkStore;
        this.metricsStore = metricsStore;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Phase3Result execute(CompletionStatus phase2Status, boolean dryRun) {
        if (dryRun) {
            log.info("Phase 3 dry-run: simulation mode, using stubbed synthesis");
            CodebaseKnowledge knowledge = simulateKnowledge();
            Phase3Result result = analyzeKnowledge(knowledge);
            persistMetrics(result, dryRun);
            return result;
        }

        log.info("Phase 3: building CodebaseKnowledge from SQLite");
        CodebaseKnowledge knowledge = buildCodebaseKnowledge();
        Phase3Result result = analyzeKnowledge(knowledge);
        persistMetrics(result, false);
        return result;
    }

    CodebaseKnowledge buildCodebaseKnowledge() {
        StructuralGraph structuralGraph = buildStructuralGraph();
        SemanticEnrichment semanticEnrichment = buildSemanticEnrichment();
        LinkRegistry linkRegistry = buildLinkRegistry();
        return new CodebaseKnowledge(structuralGraph, semanticEnrichment, linkRegistry);
    }

    private StructuralGraph buildStructuralGraph() {
        List<CallGraphEdge> edges = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.CALL_GRAPH_EDGE),
            CallGraphEdge.class);
        List<EndpointInfo> endpoints = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.ENDPOINT),
            EndpointInfo.class);
        List<DbAccessInfo> dbAccess = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.DB_ACCESS),
            DbAccessInfo.class);
        List<ComponentInfo> components = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.COMPONENT),
            ComponentInfo.class);
        return new StructuralGraph(edges, endpoints, dbAccess, components);
    }

    private SemanticEnrichment buildSemanticEnrichment() {
        List<Map<String, Object>> rows =
            executionFindingStore.findAllByType(FindingType.SEMANTIC_ENRICHMENT);
        Map<String, ExecutionFinding> byPath = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                ExecutionFinding ef = objectMapper.readValue(json, ExecutionFinding.class);
                if (ef.metadata() != null && ef.metadata().filePath() != null) {
                    byPath.put(ef.metadata().filePath(), ef);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize ExecutionFinding: {}", e.getMessage());
            }
        }
        return new SemanticEnrichment(byPath);
    }

    private LinkRegistry buildLinkRegistry() {
        List<FloatingLinkInfo> floating = floatingLinkStore.findAll();
        List<TopicLink> topics = topicLinkStore.findAll();
        return new LinkRegistry(floating, topics);
    }

    private <T> List<T> deserializeFindings(
            List<Map<String, Object>> rows, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                result.add(objectMapper.readValue(json, type));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize {}: {}", type.getSimpleName(), e.getMessage());
            }
        }
        return result;
    }

    private CodebaseKnowledge simulateKnowledge() {
        return new CodebaseKnowledge(
            new StructuralGraph(),
            new SemanticEnrichment(),
            new LinkRegistry());
    }

    static Phase3Result analyzeKnowledge(CodebaseKnowledge knowledge) {
        List<String> flowNames = knowledge.getFlowNames();
        int flowsExtracted = flowNames.size();
        if (flowsExtracted == 0) {
            flowsExtracted = knowledge.semanticEnrichment().size();
        }
        int ambiguityGaps = knowledge.findUnresolvedLinks().size()
            + knowledge.findUnresolvedTopicLinks().size();
        int awaitingReview = 0;

        return new Phase3Result(flowsExtracted, ambiguityGaps, awaitingReview, flowNames);
    }

    private void persistMetrics(Phase3Result result, boolean dryRun) {
        int totalTokens = dryRun ? 0 : 0;
        Metric metric = new Metric(
            UUID.randomUUID().toString(),
            3,
            result.flowsExtracted() + result.ambiguityGaps(),
            result.flowsExtracted(),
            0, 0, 0, 0,
            totalTokens,
            0.0,
            LocalDateTime.now().toString()
        );
        metricsStore.save(metric);
        log.info("Phase 3 metrics persisted: {} flows, {} ambiguity gaps, {} awaiting review",
            result.flowsExtracted(), result.ambiguityGaps(), result.awaitingReview());
    }
}
