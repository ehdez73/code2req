package com.github.ehdez73.code2req.extraction;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.ProcessOptions;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionConfig;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.SpecResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.EventListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExtractionOrchestrator.class);

    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final FloatingLinkStore floatingLinkStore;
    private final TopicLinkStore topicLinkStore;
    private final MetricsStore metricsStore;
    private final AgentPlatform agentPlatform;
    private final ExecutionConfig executionConfig;
    private final ObjectMapper objectMapper;

    public ExtractionOrchestrator(TaskStore taskStore, ExecutionFindingStore executionFindingStore,
                                  FloatingLinkStore floatingLinkStore, TopicLinkStore topicLinkStore,
                                  MetricsStore metricsStore, AgentPlatform agentPlatform,
                                  ExecutionConfig executionConfig) {
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.floatingLinkStore = floatingLinkStore;
        this.topicLinkStore = topicLinkStore;
        this.metricsStore = metricsStore;
        this.agentPlatform = agentPlatform;
        this.executionConfig = executionConfig;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ExtractionResult execute(boolean dryRun, boolean force) {
        if (dryRun) {
            log.info("Phase 3 dry-run: simulation mode, using stubbed synthesis");
            CodebaseKnowledge knowledge = simulateKnowledge();
            ExtractionResult result = analyzeKnowledge(knowledge);
            persistMetrics(result, true);
            return result;
        }

        log.info("Phase 3: building CodebaseKnowledge from SQLite");
        CodebaseKnowledge knowledge = buildCodebaseKnowledge();

        if (shouldSkipPhase3(knowledge, force)) {
            log.info("Phase 3: no entry points, flows, or unresolved links to process, skipping");
            ExtractionResult empty = ExtractionResult.empty();
            persistMetrics(empty, false);
            return empty;
        }

        if (!requireAllTasksTerminal(force)) {
            return ExtractionResult.blocked("All tasks must be SKIPPED or ENRICHED before Phase 3. Run 'enrich --resume' first.");
        }

//        if (agentPlatform == null) {
//            log.info("Phase 3: AgentPlatform not available (Embabel not configured), skipping agent");
//            ExtractionResult noAgentResult = ExtractionResult.empty();
//            persistMetrics(noAgentResult, false);
//            return noAgentResult;
//        }

        log.info("Phase 3: launching GOAP agent (FunctionalRequirementAgent)");

        try {
            var agent = agentPlatform.agents().stream()
                .filter(a -> "functional-requirement-extractor".equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "FunctionalRequirementAgent not deployed by Embabel"));

            Map<String, Object> initialBlackboard = new HashMap<>();
            initialBlackboard.put("codebaseKnowledge", knowledge);
            initialBlackboard.put("outputDir", Path.of("spec-output"));

            AgentProcess process = agentPlatform.createAgentProcess(
                agent, ProcessOptions.DEFAULT, initialBlackboard);
            agentPlatform.start(process).get(
                executionConfig.resolvedPhase3TimeoutMinutes(), TimeUnit.MINUTES);

            SpecResult specResult = process.resultOfType(SpecResult.class);

            List<String> flowNames = knowledge.getFlowNames();
            if (flowNames.isEmpty()) {
                flowNames = knowledge.getEntryPoints().stream()
                    .map(EntryPoint::id)
                    .distinct()
                    .toList();
            }
            int flowCount = specResult != null ? specResult.flowCount() : 0;
            int ambiguityGaps = knowledge.findUnresolvedLinks().size()
                + knowledge.findUnresolvedTopicLinks().size();

            List<Path> generatedFiles = specResult != null ?
                List.of(specResult.markdownPath(), specResult.manifestPath()) : List.of();
            ExtractionResult result = new ExtractionResult(
                flowCount, ambiguityGaps, 0, flowNames, generatedFiles);
            persistMetrics(result, false);
            return result;
        } catch (Exception e) {
            log.error("Phase 3 synthesis failed: {}", e.getMessage(), e);
            ExtractionResult result = ExtractionResult.empty();
            persistMetrics(result, false);
            return result;
        }
    }

    public ExtractionResult execute() {
        return execute(false, false);
    }

    public ExtractionResult execute(boolean dryRun) {
        return execute(dryRun, false);
    }

    boolean shouldSkipPhase3(CodebaseKnowledge knowledge, boolean force) {
        if (force) {
            return false;
        }
        return knowledge.getFlowNames().isEmpty()
            && knowledge.findUnresolvedLinks().isEmpty()
            && knowledge.findUnresolvedTopicLinks().isEmpty()
            && knowledge.getEntryPoints().isEmpty();
    }

    boolean requireAllTasksTerminal(boolean force) {
        if (force) return true;
        List<Task> allTasks = taskStore.findAll();
        List<Task> nonTerminal = allTasks.stream()
            .filter(t -> t.status() != TaskStatus.SKIPPED && t.status() != TaskStatus.ENRICHED)
            .toList();
        if (!nonTerminal.isEmpty()) {
            String reportLine = nonTerminal.stream()
                .map(t -> "  " + t.taskId() + " (" + t.filePath() + ") — " + t.status().name())
                .collect(Collectors.joining("\n"));
            log.warn("Phase 3 blocked: {} task(s) not in SKIPPED or ENRICHED:\n{}\n" +
                "Run 'enrich --resume' first.", nonTerminal.size(), reportLine);
            return false;
        }
        return true;
    }

    public CodebaseKnowledge buildCodebaseKnowledge() {
        return new CodebaseKnowledge(
            buildStructuralGraph(),
            buildSemanticEnrichment(),
            buildLinkRegistry());
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
        List<ScheduledTaskInfo> scheduledTasks = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.SCHEDULED_TASK),
            ScheduledTaskInfo.class);
        List<KafkaInfo> kafkaListeners = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.KAFKA_LISTENER),
            KafkaInfo.class);
        List<RabbitMqInfo> rabbitmqListeners = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.RABBITMQ_LISTENER),
            RabbitMqInfo.class);
        List<ActiveMqInfo> activemqListeners = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.ACTIVEMQ_LISTENER),
            ActiveMqInfo.class);
        List<EventListenerInfo> eventListeners = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.EVENT_LISTENER),
            EventListenerInfo.class);
        return new StructuralGraph(edges, endpoints, dbAccess, components,
            scheduledTasks, kafkaListeners, rabbitmqListeners,
            activemqListeners, eventListeners);
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

    private <T> List<T> deserializeFindings(List<Map<String, Object>> rows, Class<T> type) {
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

    static ExtractionResult analyzeKnowledge(CodebaseKnowledge knowledge) {
        List<String> enrichedFlowNames = knowledge.getFlowNames();
        List<String> entryPointNames = knowledge.getEntryPoints().stream()
            .map(EntryPoint::id)
            .distinct()
            .collect(Collectors.toList());
        List<String> allFlowNames = Stream.concat(
            entryPointNames.stream(),
            enrichedFlowNames.stream()
        ).distinct().collect(Collectors.toList());
        int flowsExtracted = allFlowNames.size();
        int ambiguityGaps = knowledge.findUnresolvedLinks().size()
            + knowledge.findUnresolvedTopicLinks().size();
        return new ExtractionResult(flowsExtracted, ambiguityGaps, 0, allFlowNames);
    }

    private void persistMetrics(ExtractionResult result, boolean dryRun) {
        int totalTokens = 0;
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
