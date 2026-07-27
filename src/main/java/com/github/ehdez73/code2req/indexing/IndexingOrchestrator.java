package com.github.ehdez73.code2req.indexing;

import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.common.domain.ScanTarget;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.java.BeanMethodInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlAopConfigInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlBeanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlComponentScanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlNamespaceBeanInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.Pass1DeclarationCollector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.TestImportIndex;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq.ActiveMqPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLinkResolver;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.EventListenerInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener.EventPublisherInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkResolver;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.validator.ValidatorInfo;
import com.github.ehdez73.code2req.indexing.domain.linker.AopAdviceLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.linker.AspectLinkResolver;
import com.github.ehdez73.code2req.indexing.domain.linker.ValidatorLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.linker.ValidatorLinkResolver;
import com.github.ehdez73.code2req.indexing.domain.model.ScanPipelineResult;
import com.github.ehdez73.code2req.indexing.domain.service.JavaVersionMapper;
import com.github.ehdez73.code2req.indexing.domain.service.SecretRedactor;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskIdHasher;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IndexingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IndexingOrchestrator.class);

    private static final Map<Class<? extends AnalysisFinding>, String> FINDING_TYPE_MAP = FindingType.FINDING_TYPE_MAP;

    private final Pass1DeclarationCollector pass1Collector;
    private final TestImportIndex testImportIndex;
    private final JavaAstAnalyzer astAnalyzer;
    private final SecretRedactor secretRedactor;
    private final TaskStore taskStore;
    private final TaskIdHasher taskIdHasher;
    private final TopicLinkResolver topicLinkResolver;
    private final FloatingLinkResolver floatingLinkResolver;
    private final ValidatorLinkResolver validatorLinkResolver;
    private final AspectLinkResolver aspectLinkResolver;
    private final ExecutionFindingStore executionFindingStore;
    private final TopicLinkStore topicLinkStore;
    private final FloatingLinkStore floatingLinkStore;
    private final MetricsStore metricsStore;
    private final TransactionTemplate transactionTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public IndexingOrchestrator(Pass1DeclarationCollector pass1Collector, TestImportIndex testImportIndex,
                                JavaAstAnalyzer astAnalyzer,
                                SecretRedactor secretRedactor, TaskStore taskStore, TaskIdHasher taskIdHasher,
                                TopicLinkResolver topicLinkResolver, FloatingLinkResolver floatingLinkResolver,
                                ValidatorLinkResolver validatorLinkResolver, AspectLinkResolver aspectLinkResolver,
                                ExecutionFindingStore executionFindingStore, TopicLinkStore topicLinkStore,
                                FloatingLinkStore floatingLinkStore, MetricsStore metricsStore,
                                TransactionTemplate txTemplate) {
        this.pass1Collector = pass1Collector;
        this.testImportIndex = testImportIndex;
        this.astAnalyzer = astAnalyzer;
        this.secretRedactor = secretRedactor;
        this.taskStore = taskStore;
        this.taskIdHasher = taskIdHasher;
        this.topicLinkResolver = topicLinkResolver;
        this.floatingLinkResolver = floatingLinkResolver;
        this.validatorLinkResolver = validatorLinkResolver;
        this.aspectLinkResolver = aspectLinkResolver;
        this.executionFindingStore = executionFindingStore;
        this.topicLinkStore = topicLinkStore;
        this.floatingLinkStore = floatingLinkStore;
        this.metricsStore = metricsStore;
        this.transactionTemplate = txTemplate;
    }

    public ScanPipelineResult execute(List<Path> allFiles, List<ScanTarget> targets, StringBuilder report) {
        testImportIndex.clear();
        var registry = new GlobalDeclarationRegistry();
        String runId = UUID.randomUUID().toString().substring(0, 8);

        int pass1Failed = runPass1(allFiles, targets, registry, report);
        int pass2Analyzed = 0;
        int pass2Failed = 0;
        List<AnalysisResult> allResults = new ArrayList<>();

        registry.freeze();

        for (Path file : allFiles) {
            log.info("Pass 2: analyzing {}", file.toAbsolutePath().normalize());
            configureParserForFile(file, targets);
            String targetName = targetNameForFile(file, targets);
            String sourceRoot = targetPathForFile(file, targets);
            var result = analyzeSingleFile(file, registry, targetName, sourceRoot);
            if (result == null) {
                pass2Failed++;
            } else {
                allResults.add(result);
                pass2Analyzed++;
            }
        }

        topicLinkStore.deleteAll();
        List<TopicLink> topicLinks = topicLinkResolver.resolve(allResults);
        topicLinkStore.saveAll(topicLinks);
        long topicResolved = topicLinks.stream().filter(l -> TopicLink.STATUS_RESOLVED.equals(l.resolvedStatus())).count();
        long topicPending = topicLinks.size() - topicResolved;

        floatingLinkStore.deleteAll();
        List<FloatingLinkInfo> floatingLinks = floatingLinkResolver.resolve(allResults);
        floatingLinkStore.saveAll(floatingLinks);
        long floatResolved = floatingLinks.stream().filter(l -> FloatingLinkInfo.STATUS_RESOLVED.equals(l.resolvedStatus())).count();
        long floatPending = floatingLinks.size() - floatResolved;

        List<ValidatorLinkInfo> validatorLinks = validatorLinkResolver.resolve(allFiles);
        saveLinkFindings(validatorLinks, allFiles, targets, FindingType.VALIDATOR_LINK);
        List<AopAdviceLinkInfo> aopLinks = aspectLinkResolver.resolve(allFiles);
        saveLinkFindings(aopLinks, allFiles, targets, FindingType.AOP_ADVICE_LINK);

        int totalEdges = executionFindingStore.countByType(FindingType.CALL_GRAPH_EDGE);
        int totalDbAccess = executionFindingStore.countByType(FindingType.DB_ACCESS);
        int totalHttpCalls = executionFindingStore.countByType(FindingType.OUTBOUND_HTTP_CALL);
        int edgesResolved = totalEdges + totalDbAccess + totalHttpCalls;
        int edgesUnresolved = 0;

        var metric = new Metric(runId, 1, allFiles.size(), pass2Analyzed,
            edgesResolved, edgesUnresolved,
            (int) topicResolved, floatingLinks.size(),
            0, 0.0, java.time.LocalDateTime.now().toString());
        metricsStore.save(metric);

        report.append(String.format(
            "  Pass 1 (Declaration Collection): %d file(s), %d failed%n", allFiles.size(), pass1Failed));
        report.append(String.format(
            "  Pass 2 (Full Analysis): %d file(s) analyzed, %d failed%n", pass2Analyzed, pass2Failed));
        report.append(String.format(
            "  Post-Pass (Topic Link Resolution): %d RESOLVED, %d PENDING%n", topicResolved, topicPending));
        report.append(String.format(
            "  Post-Pass (Floating Link Resolution): %d RESOLVED, %d PENDING%n", floatResolved, floatPending));
        report.append(String.format(
            "  Post-Pass (Validator Link Resolution): %d link(s)%n", validatorLinks.size()));
        report.append(String.format(
            "  Post-Pass (AOP Advice Link Resolution): %d link(s)%n", aopLinks.size()));
        report.append(String.format(
            "  Persistence: %d execution finding(s), %d topic link(s), %d floating link(s), 1 metric row(s)%n",
            executionFindingStore.count(), topicLinkStore.count(), floatingLinkStore.count()));

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

    private static String targetNameForFile(Path file, List<ScanTarget> targets) {
        return targetForFile(file, targets, ScanTarget::name);
    }

    private static String targetPathForFile(Path file, List<ScanTarget> targets) {
        return targetForFile(file, targets, ScanTarget::path);
    }

    private static <T> T targetForFile(Path file, List<ScanTarget> targets,
                                        java.util.function.Function<ScanTarget, T> extractor) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        Path normalized = file.toAbsolutePath().normalize();
        for (ScanTarget target : targets) {
            if (normalized.startsWith(Path.of(target.path()).normalize())) {
                return extractor.apply(target);
            }
        }
        return null;
    }

    private int runPass1(List<Path> files, List<ScanTarget> targets, GlobalDeclarationRegistry registry, StringBuilder report) {
        int failed = 0;
        for (Path file : files) {
            String fp = file.toAbsolutePath().normalize().toString();
            log.info("Pass 1: collecting declarations from {}", fp);
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String redacted = secretRedactor.redact(content);
                configureParserForFile(file, targets);
                CompilationUnit cu = StaticJavaParser.parse(redacted);
                pass1Collector.collect(cu, registry, fp);
                testImportIndex.index(fp, cu);
            } catch (Exception e) {
                log.warn("Pass 1 failed for {}: {}", fp, e.getMessage());
                failed++;
            }
        }
        return failed;
    }

    private AnalysisResult analyzeSingleFile(Path file, GlobalDeclarationRegistry registry,
                                              String targetName, String sourceRoot) {
        String fp = file.toAbsolutePath().normalize().toString();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read {}: {}", fp, e.getMessage());
            storeFailedTask(fp, targetName);
            return null;
        }

        String redactedContent = secretRedactor.redact(content);
        AnalysisContext context = new AnalysisContext(fp, sourceRoot != null ? sourceRoot : "", registry);
        AnalysisResult result = astAnalyzer.analyze(fp, redactedContent, context);

        String contentHash = sha256Hex(content);
        String taskId = taskIdHasher.hash(fp, contentHash, targetName);

        transactionTemplate.executeWithoutResult(status -> {
            taskStore.save(new Task(taskId, fp, TaskStatus.INDEXED, "java", contentHash, targetName));
            executionFindingStore.deleteByTaskId(taskId);
            persistFindings(taskId, result);
            reclassifyFindings(taskId, result);
        });

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

    private void storeFailedTask(String filePath, String targetName) {
        var failedTask = new Task(
            taskIdHasher.hash(filePath, "unreadable", targetName),
            filePath, TaskStatus.FAILED, "java", "unreadable", targetName);
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

    private <T extends AnalysisFinding> void saveLinkFindings(List<T> links, List<Path> allFiles,
                                                               List<ScanTarget> targets, String findingType) {
        if (links.isEmpty()) return;
        for (T link : links) {
            String sourceFile = link.filePath();
            if (sourceFile == null) continue;
            String targetName = targetNameForFile(Path.of(sourceFile), targets);
            try {
                String content = Files.readString(Path.of(sourceFile), StandardCharsets.UTF_8);
                String contentHash = sha256Hex(content);
                String taskId = taskIdHasher.hash(sourceFile, contentHash, targetName);
                saveGranularFinding(taskId, link, findingType);
            } catch (Exception e) {
                log.warn("Failed to persist {} for {}: {}", findingType, sourceFile, e.getMessage());
            }
        }
    }

    private static long elapsedSeconds(Instant start) {
        return Duration.between(start, Instant.now()).toSeconds();
    }
}
