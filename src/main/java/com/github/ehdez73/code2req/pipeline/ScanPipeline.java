package com.github.ehdez73.code2req.pipeline;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.bean.java.BeanMethodInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlAopConfigInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlBeanInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlComponentScanInfo;
import com.github.ehdez73.code2req.analyzer.bean.xml.XmlNamespaceBeanInfo;
import com.github.ehdez73.code2req.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.ehdez73.code2req.analyzer.declaration.Pass1DeclarationCollector;
import com.github.ehdez73.code2req.analyzer.event.listener.EventListenerInfo;
import com.github.ehdez73.code2req.analyzer.event.listener.EventPublisherInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.activemq.ActiveMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.kafka.KafkaPublisherInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.analyzer.event.broker.rabbitmq.RabbitMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLinkResolver;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkResolver;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.analyzer.validator.ValidatorInfo;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.config.JavaVersionMapper;
import com.github.ehdez73.code2req.config.SecretRedactor;
import com.github.ehdez73.code2req.model.Metric;
import com.github.ehdez73.code2req.model.ScanTarget;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FindingType;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScanPipeline {

    private static final Logger log = LoggerFactory.getLogger(ScanPipeline.class);

    private static final Map<Class<? extends AnalysisFinding>, String> FINDING_TYPE_MAP = Map.ofEntries(
        Map.entry(ComponentInfo.class, FindingType.COMPONENT),
        Map.entry(EndpointInfo.class, FindingType.ENDPOINT),
        Map.entry(ScheduledTaskInfo.class, FindingType.SCHEDULED_TASK),
        Map.entry(EventListenerInfo.class, FindingType.EVENT_LISTENER),
        Map.entry(EventPublisherInfo.class, FindingType.EVENT_PUBLISHER),
        Map.entry(ValidatorInfo.class, FindingType.VALIDATOR),
        Map.entry(KafkaInfo.class, FindingType.KAFKA_LISTENER),
        Map.entry(KafkaPublisherInfo.class, FindingType.KAFKA_PUBLISHER),
        Map.entry(BeanMethodInfo.class, FindingType.BEAN_METHOD),
        Map.entry(RabbitMqInfo.class, FindingType.RABBITMQ_LISTENER),
        Map.entry(RabbitMqPublisherInfo.class, FindingType.RABBITMQ_PUBLISHER),
        Map.entry(ActiveMqInfo.class, FindingType.ACTIVEMQ_LISTENER),
        Map.entry(ActiveMqPublisherInfo.class, FindingType.ACTIVEMQ_PUBLISHER),
        Map.entry(XmlBeanInfo.class, FindingType.XML_BEAN),
        Map.entry(DbAccessInfo.class, FindingType.DB_ACCESS),
        Map.entry(XmlComponentScanInfo.class, FindingType.XML_COMPONENT_SCAN),
        Map.entry(XmlAopConfigInfo.class, FindingType.XML_AOP_CONFIG),
        Map.entry(XmlNamespaceBeanInfo.class, FindingType.XML_NAMESPACE_BEAN),
        Map.entry(CallGraphEdge.class, FindingType.CALL_GRAPH_EDGE),
        Map.entry(OutboundHttpCallInfo.class, FindingType.OUTBOUND_HTTP_CALL)
    );

    private final Pass1DeclarationCollector pass1Collector;
    private final JavaAstAnalyzer astAnalyzer;
    private final SecretRedactor secretRedactor;
    private final TaskStore taskStore;
    private final TaskIdHasher taskIdHasher;
    private final TopicLinkResolver topicLinkResolver;
    private final FloatingLinkResolver floatingLinkResolver;
    private final ExecutionFindingStore executionFindingStore;
    private final TopicLinkStore topicLinkStore;
    private final FloatingLinkStore floatingLinkStore;
    private final MetricsStore metricsStore;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ScanPipeline(
            Pass1DeclarationCollector pass1Collector,
            JavaAstAnalyzer astAnalyzer,
            SecretRedactor secretRedactor,
            TaskStore taskStore,
            TaskIdHasher taskIdHasher,
            TopicLinkResolver topicLinkResolver,
            FloatingLinkResolver floatingLinkResolver,
            ExecutionFindingStore executionFindingStore,
            TopicLinkStore topicLinkStore,
            FloatingLinkStore floatingLinkStore,
            MetricsStore metricsStore) {
        this.pass1Collector = pass1Collector;
        this.astAnalyzer = astAnalyzer;
        this.secretRedactor = secretRedactor;
        this.taskStore = taskStore;
        this.taskIdHasher = taskIdHasher;
        this.topicLinkResolver = topicLinkResolver;
        this.floatingLinkResolver = floatingLinkResolver;
        this.executionFindingStore = executionFindingStore;
        this.topicLinkStore = topicLinkStore;
        this.floatingLinkStore = floatingLinkStore;
        this.metricsStore = metricsStore;
    }

    public ScanPipelineResult execute(List<Path> files, StringBuilder report) {
        return execute(files, List.of(), report);
    }

    public ScanPipelineResult execute(List<Path> files, List<ScanTarget> targets, StringBuilder report) {
        var phaseStart = Instant.now();
        var registry = new GlobalDeclarationRegistry();
        String runId = UUID.randomUUID().toString().substring(0, 8);

        int pass1Failed = runPass1(files, targets, registry, report);
        int pass2Analyzed = 0;
        int pass2Failed = 0;
        List<AnalysisResult> allResults = new ArrayList<>();

        registry.freeze();

        for (Path file : files) {
            configureParserForFile(file, targets);
            var result = analyzeSingleFile(file, registry);
            if (result == null) {
                pass2Failed++;
            } else {
                allResults.add(result);
                pass2Analyzed++;
            }
        }

        List<TopicLink> topicLinks = topicLinkResolver.resolve(allResults);
        topicLinkStore.saveAll(topicLinks);
        long topicResolved = topicLinks.stream().filter(l -> TopicLink.STATUS_RESOLVED.equals(l.resolvedStatus())).count();
        long topicPending = topicLinks.size() - topicResolved;

        List<FloatingLinkInfo> floatingLinks = floatingLinkResolver.resolve(allResults);
        floatingLinkStore.saveAll(floatingLinks);
        long floatResolved = floatingLinks.stream().filter(l -> FloatingLinkInfo.STATUS_RESOLVED.equals(l.resolvedStatus())).count();
        long floatPending = floatingLinks.size() - floatResolved;

        int totalEdges = executionFindingStore.countByType(FindingType.CALL_GRAPH_EDGE);
        int totalDbAccess = executionFindingStore.countByType(FindingType.DB_ACCESS);
        int totalHttpCalls = executionFindingStore.countByType(FindingType.OUTBOUND_HTTP_CALL);
        int edgesResolved = totalEdges + totalDbAccess + totalHttpCalls;
        int edgesUnresolved = 0;

        var metric = new Metric(runId, 1, files.size(), pass2Analyzed,
            edgesResolved, edgesUnresolved,
            (int) topicResolved, floatingLinks.size(),
            0, 0.0, java.time.LocalDateTime.now().toString());
        metricsStore.save(metric);

        report.append(String.format(
            "  Pass 1 (Declaration Collection): %d file(s), %d failed%n", files.size(), pass1Failed));
        report.append(String.format(
            "  Pass 2 (Full Analysis): %d file(s) analyzed, %d failed%n", pass2Analyzed, pass2Failed));
        report.append(String.format(
            "  Post-Pass (Topic Link Resolution): %d RESOLVED, %d PENDING%n", topicResolved, topicPending));
        report.append(String.format(
            "  Post-Pass (Floating Link Resolution): %d RESOLVED, %d PENDING%n", floatResolved, floatPending));
        report.append(String.format(
            "  Persistence: %d execution finding(s), %d topic link(s), %d floating link(s), 1 metric row(s)%n",
            executionFindingStore.count(), topicLinkStore.count(), floatingLinkStore.count()));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));

        return new ScanPipelineResult(allResults, registry, pass2Analyzed, pass2Failed, topicLinks, floatingLinks);
    }

    private void configureParserForFile(Path file, List<ScanTarget> targets) {
        ParserConfiguration.LanguageLevel level = languageLevelForFile(file, targets);
        StaticJavaParser.getConfiguration().setLanguageLevel(level);
    }

    private static ParserConfiguration.LanguageLevel languageLevelForFile(Path file, List<ScanTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return ParserConfiguration.LanguageLevel.JAVA_17;
        }
        Path normalized = file.toAbsolutePath().normalize();
        for (ScanTarget target : targets) {
            if (normalized.startsWith(Path.of(target.path()).normalize())) {
                return JavaVersionMapper.toLanguageLevel(target.javaVersionOrDefault());
            }
        }
        return ParserConfiguration.LanguageLevel.JAVA_17;
    }

    private int runPass1(List<Path> files, List<ScanTarget> targets, GlobalDeclarationRegistry registry, StringBuilder report) {
        int failed = 0;
        for (Path file : files) {
            String fp = file.toString();
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String redacted = secretRedactor.redact(content);
                configureParserForFile(file, targets);
                CompilationUnit cu = StaticJavaParser.parse(redacted);
                pass1Collector.collect(cu, registry, fp);
            } catch (Exception e) {
                log.warn("Pass 1 failed for {}: {}", fp, e.getMessage());
                failed++;
            }
        }
        return failed;
    }

    private AnalysisResult analyzeSingleFile(Path file, GlobalDeclarationRegistry registry) {
        String fp = file.toString();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read {}: {}", fp, e.getMessage());
            storeFailedTask(fp);
            return null;
        }

        String redactedContent = secretRedactor.redact(content);
        AnalysisContext context = new AnalysisContext(fp, "", registry);
        AnalysisResult result = astAnalyzer.analyze(fp, redactedContent, context);

        String contentHash = sha256Hex(content);
        String taskId = taskIdHasher.hash(fp, contentHash);
        taskStore.save(new Task(taskId, fp, TaskStatus.SUCCESS, "java", contentHash));

        persistFindings(taskId, result);
        reclassifyFindings(taskId, result);

        return result;
    }

    private void persistFindings(String taskId, AnalysisResult result) {
        for (var entry : FINDING_TYPE_MAP.entrySet()) {
            var findings = result.findings(entry.getKey());
            if (!findings.isEmpty()) {
                executionFindingStore.saveAllForTask(taskId, findings, entry.getValue());
            }
        }
    }

    private void reclassifyFindings(String taskId, AnalysisResult result) {
        for (DbAccessInfo dba : result.findings(DbAccessInfo.class)) {
            String granularType = switch (dba.type()) {
                case "SPRING_DATA" -> FindingType.SPRING_DATA_INTERFACE;
                case "PROCEDURE" -> FindingType.DATABASE_PROCEDURE_CALL;
                case "NATIVE_SQL" -> FindingType.NATIVE_SQL_QUERY;
                case "JPQL_HQL" -> FindingType.JPQL_HQL_QUERY;
                default -> null;
            };
            if (granularType != null) {
                saveGranularFinding(taskId, dba, granularType);
            }
        }
        for (ValidatorInfo vi : result.findings(ValidatorInfo.class)) {
            if (!vi.isBuiltIn() && vi.isValidBody() != null && !vi.isValidBody().isBlank()) {
                saveGranularFinding(taskId, vi, FindingType.CONSTRAINT_VALIDATOR);
            }
        }
    }

    private void saveGranularFinding(String taskId, AnalysisFinding finding, String findingType) {
        try {
            String json = MAPPER.writeValueAsString(finding);
            executionFindingStore.save(taskId, findingType, json, true);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {} for granular finding {}: {}", findingType, taskId, e.getMessage());
        }
    }

    private void storeFailedTask(String filePath) {
        var failedTask = new Task(
            taskIdHasher.hash(filePath, "unreadable"),
            filePath, TaskStatus.FAILED, "java", "unreadable");
        taskStore.save(failedTask);
    }

    private static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static long elapsedSeconds(Instant start) {
        return Duration.between(start, Instant.now()).toSeconds();
    }
}
