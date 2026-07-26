package com.github.ehdez73.code2req.extraction;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.ProcessOptions;
import com.github.ehdez73.code2req.extraction.domain.model.ExtractionConfig;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.ActiveMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.QuarantineConfig;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.QualifierInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.java.BeanMethodInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlBeanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlJmsListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlScheduledTaskInfo;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExtractionOrchestrator.class);

    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final FloatingLinkStore floatingLinkStore;
    private final TopicLinkStore topicLinkStore;
    private final MetricsStore metricsStore;
    private final AgentPlatform agentPlatform;
    private final ExtractionConfig extractionConfig;
    private final QuarantineConfig quarantineConfig;
    private final ObjectMapper objectMapper;
    private final Path cachePath;
    private final Path specDir;

    @Autowired
    public ExtractionOrchestrator(TaskStore taskStore, ExecutionFindingStore executionFindingStore,
                                   FloatingLinkStore floatingLinkStore, TopicLinkStore topicLinkStore,
                                   MetricsStore metricsStore, AgentPlatform agentPlatform,
                                   ExtractionConfig extractionConfig, QuarantineConfig quarantineConfig,
                                   @Value("${code2req.output.spec-dir:./spec-output}") String specDir,
                                   @Value("${code2req.output.extraction-cache-file:extraction-cache.json}") String cacheFile) {
        this(taskStore, executionFindingStore, floatingLinkStore, topicLinkStore,
             metricsStore, agentPlatform, extractionConfig, quarantineConfig,
             Path.of(specDir).resolve(cacheFile).normalize(),
             Path.of(specDir).normalize());
    }

    public ExtractionOrchestrator(TaskStore taskStore, ExecutionFindingStore executionFindingStore,
                            FloatingLinkStore floatingLinkStore, TopicLinkStore topicLinkStore,
                            MetricsStore metricsStore, AgentPlatform agentPlatform,
                            ExtractionConfig extractionConfig, QuarantineConfig quarantineConfig) {
        this(taskStore, executionFindingStore, floatingLinkStore, topicLinkStore,
             metricsStore, agentPlatform, extractionConfig, quarantineConfig,
             Path.of("./spec-output").resolve("extraction-cache.json").normalize(),
             Path.of("./spec-output").normalize());
    }

    private ExtractionOrchestrator(TaskStore taskStore, ExecutionFindingStore executionFindingStore,
                                    FloatingLinkStore floatingLinkStore, TopicLinkStore topicLinkStore,
                                    MetricsStore metricsStore, AgentPlatform agentPlatform,
                                    ExtractionConfig extractionConfig, QuarantineConfig quarantineConfig,
                                    Path cachePath, Path specDir) {
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.floatingLinkStore = floatingLinkStore;
        this.topicLinkStore = topicLinkStore;
        this.metricsStore = metricsStore;
        this.agentPlatform = agentPlatform;
        this.extractionConfig = extractionConfig;
        this.quarantineConfig = quarantineConfig;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.cachePath = cachePath;
        this.specDir = specDir;
    }

    public ExtractionResult execute(boolean dryRun, boolean force, boolean resume) {
        if (resume && force) {
            throw new IllegalArgumentException("--resume and --force are mutually exclusive");
        }

        if (dryRun) {
            log.info("Phase 3 dry-run: simulation mode, using stubbed synthesis");
            CodebaseKnowledge knowledge = simulateKnowledge();
            ExtractionResult result = analyzeKnowledge(knowledge);
            persistMetrics(result, true);
            return result;
        }

        boolean gateOk = resume || force;
        if (!requireAllTasksTerminal(gateOk)) {
            return ExtractionResult.blocked("All tasks must be SKIPPED, ENRICHED, or INDEXED before Phase 3. Run 'enrich --resume' first.");
        }

        if (force) {
            executionFindingStore.deleteAllByType(FindingType.FLOW_ANALYSIS);
            log.info("Force mode: deleted all cached FLOW_ANALYSIS findings");
        }

        log.info("Phase 3: building CodebaseKnowledge from SQLite");
        CodebaseKnowledge knowledge = buildCodebaseKnowledge();

        if (shouldSkipPhase3(knowledge, force)) {
            log.info("Phase 3: no entry points, flows, or unresolved links to process, skipping");
            ExtractionResult empty = ExtractionResult.empty();
            persistMetrics(empty, false);
            return empty;
        }

        log.info("Phase 3: launching GOAP agent (FunctionalRequirementAgent)");

        try {
            var agent = agentPlatform.agents().stream()
                .filter(a -> "functional-requirement-extractor".equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "FunctionalRequirementAgent not deployed by Embabel"));

            Map<String, Object> initialBlackboard = new HashMap<>();
            initialBlackboard.put("codebaseKnowledge", knowledge);
            initialBlackboard.put("outputDir", specDir);
            initialBlackboard.put("extractionConfig", extractionConfig);
            initialBlackboard.put("quarantineConfig", quarantineConfig);
            initialBlackboard.put("resume", resume);

            AgentProcess process = agentPlatform.createAgentProcess(
                agent, ProcessOptions.DEFAULT, initialBlackboard);
            agentPlatform.start(process).get(
                extractionConfig.resolvedPhase3TimeoutMinutes(), TimeUnit.MINUTES);

            ExtractionCache cache = process.resultOfType(ExtractionCache.class);

            List<String> flowNames = knowledge.getEntryPoints().stream()
                .map(EntryPoint::id)
                .distinct()
                .toList();
            int ambiguityGaps = cache != null ? cache.quarantineGaps().size() : 0;
            int flowCount = cache != null ? cache.crossRefResult().features().stream()
                .mapToInt(f -> f.flows().size()).sum() : 0;

            List<Path> generatedFiles = List.of(cachePath);
            ExtractionResult result = new ExtractionResult( flowCount, ambiguityGaps, 0, flowNames, generatedFiles);

            persistMetrics(result, false);
            log.info("Created extraction cache: {}", cachePath);
            return result;
        } catch (Exception e) {
            log.error("Phase 3 synthesis failed: {}", e.getMessage(), e);
            ExtractionResult result = ExtractionResult.empty();
            persistMetrics(result, false);
            return result;
        }
    }

    public ExtractionResult execute() {
        return execute(false, false, false);
    }

    public ExtractionResult execute(boolean dryRun) {
        return execute(dryRun, false, false);
    }

    public ExtractionResult execute(boolean dryRun, boolean force) {
        return execute(dryRun, force, false);
    }

    boolean shouldSkipPhase3(CodebaseKnowledge knowledge, boolean force) {
        if (force) {
            return false;
        }
        return knowledge.findUnresolvedLinks().isEmpty()
            && knowledge.findUnresolvedTopicLinks().isEmpty()
            && knowledge.getEntryPoints().isEmpty();
    }

    boolean requireAllTasksTerminal(boolean force) {
        if (force) return true;
        List<Task> allTasks = taskStore.findAll();
        List<Task> nonTerminal = allTasks.stream()
            .filter(t -> t.status() != TaskStatus.SKIPPED
                && t.status() != TaskStatus.ENRICHED
                && t.status() != TaskStatus.INDEXED)
            .toList();
        if (!nonTerminal.isEmpty()) {
            String reportLine = nonTerminal.stream()
                .map(t -> "  " + t.taskId() + " (" + t.filePath() + ") — " + t.status().name())
                .collect(Collectors.joining("\n"));
            log.warn("Phase 3 blocked: {} task(s) not in SKIPPED, ENRICHED, or INDEXED:\n{}\n" +
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
        List<XmlScheduledTaskInfo> xmlScheduledTasks = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.XML_SCHEDULED_TASK),
            XmlScheduledTaskInfo.class);
        List<XmlJmsListenerInfo> xmlJmsListeners = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.XML_JMS_LISTENER),
            XmlJmsListenerInfo.class);
        List<XmlBeanInfo> xmlBeans = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.XML_BEAN),
            XmlBeanInfo.class);
        List<BeanMethodInfo> beanMethods = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.BEAN_METHOD),
            BeanMethodInfo.class);
        List<QualifierInfo> qualifiers = deserializeFindings(
            executionFindingStore.findAllByType(FindingType.QUALIFIER),
            QualifierInfo.class);

        Map<String, String> beanRegistry = new HashMap<>();
        for (XmlBeanInfo xb : xmlBeans) {
            if (xb.beanId() != null && !xb.beanId().isEmpty()
                && xb.className() != null && !xb.className().isEmpty()) {
                beanRegistry.put(xb.beanId(), xb.className());
            }
        }

        Map<String, String> classToFileMap = new HashMap<>();
        for (CallGraphEdge edge : edges) {
            if (edge.sourceClassName() != null && !edge.sourceClassName().isBlank()
                && edge.sourceFilePath() != null && !edge.sourceFilePath().isBlank()) {
                classToFileMap.putIfAbsent(edge.sourceClassName(), edge.sourceFilePath());
            }
            if (edge.isResolved() && edge.targetClassName() != null && !edge.targetClassName().isBlank()
                && edge.targetFilePath() != null && !edge.targetFilePath().isBlank()) {
                classToFileMap.putIfAbsent(edge.targetClassName(), edge.targetFilePath());
            }
        }
        for (ComponentInfo ci : components) {
            if (ci.className() != null && ci.filePath() != null) {
                classToFileMap.putIfAbsent(ci.className(), ci.filePath());
            }
        }

        Set<String> knownBeanClasses = new HashSet<>();
        Set<String> xmlBeanClassNames = new HashSet<>();
        for (XmlBeanInfo xb : xmlBeans) {
            if (xb.className() != null && !xb.className().isEmpty()) {
                String simpleName = xb.className().contains(".")
                    ? xb.className().substring(xb.className().lastIndexOf('.') + 1)
                    : xb.className();
                knownBeanClasses.add(simpleName);
                xmlBeanClassNames.add(simpleName);
            }
        }
        for (ComponentInfo ci : components) {
            if (ci.className() != null && !ci.className().isEmpty()) {
                knownBeanClasses.add(ci.className());
            }
        }
        for (BeanMethodInfo bm : beanMethods) {
            if (bm.returnType() != null && !bm.returnType().isEmpty()) {
                knownBeanClasses.add(bm.returnType());
            }
        }

        Map<String, String> wiringMap = new HashMap<>();

        Set<String> primaryClasses = components.stream()
            .filter(ComponentInfo::primary)
            .map(ComponentInfo::className)
            .collect(Collectors.toSet());

        Map<String, String> qualifierClassMap = new HashMap<>();
        Set<String> qualifierValues = new HashSet<>();
        for (QualifierInfo q : qualifiers) {
            String qv = q.qualifierValue();
            qualifierValues.add(qv);
            if (beanRegistry.containsKey(qv)) {
                String fqn = beanRegistry.get(qv);
                String simpleClass = fqn.contains(".")
                    ? fqn.substring(fqn.lastIndexOf('.') + 1)
                    : fqn;
                qualifierClassMap.put(qv, simpleClass);
            }
        }

        for (CallGraphEdge edge : edges) {
            if (CallGraphEdge.STATUS_AMBIGUOUS.equals(edge.resolvedStatus())) {
                String interfaceName = edge.targetClassName();
                List<String> candidateClasses = edge.ambiguousCandidates().stream()
                    .map(c -> c.contains(".") ? c.substring(0, c.indexOf('.')) : c)
                    .toList();
                String resolved = null;

                for (String candidateClass : candidateClasses) {
                    if (primaryClasses.contains(candidateClass)) {
                        resolved = candidateClass;
                        break;
                    }
                }
                if (resolved != null) {
                    log.info("Wiring: {} → {} [@Primary]", interfaceName, resolved);
                }

                if (resolved == null) {
                    for (String candidateClass : candidateClasses) {
                        if (qualifierClassMap.containsValue(candidateClass)) {
                            resolved = candidateClass;
                            break;
                        }
                    }
                    if (resolved != null) {
                        log.info("Wiring: {} → {} [@Qualifier resolved via bean registry]", interfaceName, resolved);
                    }
                }

                if (resolved == null) {
                    for (String candidateClass : candidateClasses) {
                        String conventionName = Character.toLowerCase(candidateClass.charAt(0)) + candidateClass.substring(1);
                        if (qualifierValues.contains(conventionName)) {
                            resolved = candidateClass;
                            break;
                        }
                    }
                    if (resolved != null) {
                        log.info("Wiring: {} → {} [@Qualifier convention match]", interfaceName, resolved);
                    }
                }

                if (resolved == null) {
                    String xmlBean = null;
                    int xmlMatchCount = 0;
                    for (String candidateClass : candidateClasses) {
                        if (xmlBeanClassNames.contains(candidateClass)) {
                            xmlBean = candidateClass;
                            xmlMatchCount++;
                        }
                    }
                    if (xmlMatchCount == 1) {
                        resolved = xmlBean;
                    }
                    if (resolved != null) {
                        log.info("Wiring: {} → {} [XML bean priority]", interfaceName, resolved);
                    }
                }

                if (resolved == null) {
                    String wiredBean = null;
                    int matchCount = 0;
                    for (String candidateClass : candidateClasses) {
                        if (knownBeanClasses.contains(candidateClass)) {
                            wiredBean = candidateClass;
                            matchCount++;
                        }
                    }
                    if (matchCount == 1) {
                        resolved = wiredBean;
                    }
                    if (resolved != null) {
                        log.info("Wiring: {} → {} [single bean match]", interfaceName, resolved);
                    }
                }

                if (resolved != null) {
                    wiringMap.put(interfaceName, resolved);
                } else {
                    log.info("Wiring: {} → unresolved ({} candidates: {})", interfaceName, candidateClasses.size(), String.join(", ", candidateClasses));
                }
            }
        }

        Map<String, ScheduledEntryPoint> resolvedScheduled = new HashMap<>();
        for (XmlScheduledTaskInfo xst : xmlScheduledTasks) {
            String schedule = xst.cron() != null ? xst.cron()
                : (xst.fixedRate() != null ? "fixedRate=" + xst.fixedRate()
                : "fixedDelay=" + xst.fixedDelay());
            String resolvedClassName = beanRegistry.getOrDefault(xst.className(), xst.className());
            String simpleClassName = resolvedClassName.contains(".")
                ? resolvedClassName.substring(resolvedClassName.lastIndexOf('.') + 1)
                : resolvedClassName;
            String xmlFilePath = xst.filePath();
            String javaFilePath = classToFileMap.get(simpleClassName);
            String filePath = javaFilePath != null ? javaFilePath : xmlFilePath;
            int methodStartLine = 0, methodEndLine = 0;
            if (javaFilePath != null) {
                int[] range = findMethodInFile(javaFilePath, xst.method(), 0);
                if (range != null) {
                    methodStartLine = range[0];
                    methodEndLine = range[1];
                }
            }
            String id = xmlFilePath + ":" + resolvedClassName + ":" + xst.method() + " " + schedule;
            resolvedScheduled.put(id, new ScheduledEntryPoint(
                id, simpleClassName, xst.method(), filePath,
                0.0, false, schedule, methodStartLine, methodEndLine,
                javaFilePath != null ? xmlFilePath : null));
        }

        Map<String, ActiveMqEntryPoint> resolvedJms = new HashMap<>();
        for (XmlJmsListenerInfo xjl : xmlJmsListeners) {
            String resolvedClassName = beanRegistry.getOrDefault(xjl.beanName(), xjl.beanName());
            String filePath = xjl.filePath();
            String id = filePath + ":" + resolvedClassName + ":" + xjl.method() + " " + xjl.destination();
            resolvedJms.put(id, new ActiveMqEntryPoint(
                id, resolvedClassName, xjl.method(), filePath,
                0.0, false, xjl.destination(), null));
        }

        return new StructuralGraph(edges, endpoints, dbAccess, components,
            scheduledTasks, kafkaListeners, rabbitmqListeners,
            activemqListeners, eventListeners, xmlScheduledTasks, xmlJmsListeners,
            new ArrayList<>(resolvedScheduled.values()), new ArrayList<>(resolvedJms.values()),
            wiringMap, classToFileMap);
    }

    private SemanticEnrichment buildSemanticEnrichment() {
        List<Map<String, Object>> rows =
            executionFindingStore.findAllByType(FindingType.SEMANTIC_ENRICHMENT);
        log.info("Phase 3: loading {} SEMANTIC_ENRICHMENT findings", rows.size());
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
        log.info("Phase 3: loading {} {} findings", rows.size(), type.getSimpleName());
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
        List<String> allFlowNames = knowledge.getEntryPoints().stream()
            .map(EntryPoint::id)
            .distinct()
            .collect(Collectors.toList());
        int flowsExtracted = allFlowNames.size();
        int ambiguityGaps = knowledge.findUnresolvedLinks().size()
            + knowledge.findUnresolvedTopicLinks().size();
        return new ExtractionResult(flowsExtracted, ambiguityGaps, 0, allFlowNames);
    }

    static int[] findMethodInFile(String filePath, String methodName, int argCount) {
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int methodIdx = line.indexOf(methodName + "(");
                if (methodIdx < 0) continue;

                int parenStart = methodIdx + methodName.length();
                int parenDepth = 0;
                StringBuilder params = new StringBuilder();
                for (int k = parenStart; k < line.length(); k++) {
                    char c = line.charAt(k);
                    if (c == '(') { parenDepth++; if (parenDepth == 1) continue; }
                    if (c == ')') { parenDepth--; if (parenDepth == 0) break; }
                    if (parenDepth == 1) params.append(c);
                }
                if (parenDepth > 0) {
                    for (int j = i + 1; j < lines.size(); j++) {
                        String nextLine = lines.get(j);
                        for (int k = 0; k < nextLine.length(); k++) {
                            char c = nextLine.charAt(k);
                            if (c == '(') { parenDepth++; if (parenDepth == 1) break; }
                            if (c == ')') { parenDepth--; if (parenDepth == 0) break; }
                            if (parenDepth == 1) params.append(c);
                        }
                        if (parenDepth == 0) break;
                    }
                }

                int actualArgCount = params.toString().strip().isEmpty() ? 0 : params.toString().split(",").length;
                if (actualArgCount != argCount) continue;

                int startLine = i + 1;
                int braceDepth = 0;
                String firstLine = lines.get(i);
                int braceStart = firstLine.indexOf('{');
                if (braceStart >= 0) {
                    for (int k = braceStart; k < firstLine.length(); k++) {
                        if (firstLine.charAt(k) == '{') braceDepth++;
                        else if (firstLine.charAt(k) == '}') braceDepth--;
                    }
                }
                if (braceDepth > 0) {
                    for (int j = i + 1; j < lines.size(); j++) {
                        String currentLine = lines.get(j);
                        for (int k = 0; k < currentLine.length(); k++) {
                            if (currentLine.charAt(k) == '{') braceDepth++;
                            else if (currentLine.charAt(k) == '}') braceDepth--;
                        }
                        if (braceDepth == 0) {
                            return new int[]{startLine, j + 1};
                        }
                    }
                }
                return new int[]{startLine, startLine};
            }
        } catch (IOException e) {
            log.warn("Could not scan {} for method {}: {}", filePath, methodName, e.getMessage());
        }
        return null;
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
