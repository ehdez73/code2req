package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.extraction.adapter.llm.LlmEnrichmentService;
import com.github.ehdez73.code2req.extraction.domain.model.EnrichmentConfig;
import com.github.ehdez73.code2req.extraction.adapter.llm.TestFileMatcher;
import com.github.ehdez73.code2req.extraction.domain.service.StructuralContextAssembler;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.EnrichedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.ComplexityLevel;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.indexing.domain.linker.AopAdviceLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.linker.ValidatorLinkInfo;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class EnrichFlowAction {

    private static final Logger log = LoggerFactory.getLogger(EnrichFlowAction.class);

    private final LlmEnrichmentService enrichmentService;
    private final EnrichmentConfig enrichmentConfig;
    private final ExecutionFindingStore executionFindingStore;
    private final TestFileMatcher testFileMatcher;
    private final LinkRegistry linkRegistry;
    private final TaskStore taskStore;
    private final StructuralContextAssembler structuralContextAssembler;
    private final ObjectMapper objectMapper;

    public EnrichFlowAction(LlmEnrichmentService enrichmentService,
                            EnrichmentConfig enrichmentConfig,
                            ExecutionFindingStore executionFindingStore,
                            TestFileMatcher testFileMatcher,
                            LinkRegistry linkRegistry,
                            TaskStore taskStore,
                            StructuralContextAssembler structuralContextAssembler,
                            ObjectMapper objectMapper) {
        this.enrichmentService = enrichmentService;
        this.enrichmentConfig = enrichmentConfig;
        this.executionFindingStore = executionFindingStore;
        this.testFileMatcher = testFileMatcher;
        this.linkRegistry = linkRegistry;
        this.taskStore = taskStore;
        this.structuralContextAssembler = structuralContextAssembler;
        this.objectMapper = objectMapper;
    }

    public EnrichedFlowResult enrich(List<ExecutionFlow> flows, CodebaseKnowledge knowledge) {
        if (flows.isEmpty()) {
            log.info("EnrichFlowAction: no flows to enrich, skipping");
            return new EnrichedFlowResult(flows);
        }

        var aopLinksByTarget = loadAopLinksByTarget();
        var validatorLinksBySource = loadValidatorLinksBySource();

        for (ExecutionFlow flow : flows) {
            try {
                enrichOneFlow(flow, knowledge, aopLinksByTarget, validatorLinksBySource);
            } catch (Exception e) {
                log.warn("EnrichFlowAction: failed to enrich flow {}: {}",
                    flow.flowId(), e.getMessage());
            }
        }

        log.info("EnrichFlowAction: completed enrichment for {} flow(s)", flows.size());
        return new EnrichedFlowResult(flows);
    }

    private void enrichOneFlow(ExecutionFlow flow, CodebaseKnowledge knowledge,
                                Map<String, List<AopAdviceLinkInfo>> aopLinksByTarget,
                                Map<String, List<ValidatorLinkInfo>> validatorLinksBySource) {

        Set<String> allFiles = new HashSet<>();

        flow.steps().stream()
            .map(FlowStep::sourceFile)
            .filter(Objects::nonNull)
            .forEach(allFiles::add);

        allFiles.add(flow.entryPoint().filePath());

        Set<String> linkedFiles = new HashSet<>();
        for (String filePath : allFiles) {
            List<AopAdviceLinkInfo> aopLinks = aopLinksByTarget.get(filePath);
            if (aopLinks != null) {
                aopLinks.forEach(l -> linkedFiles.add(l.targetFile()));
            }
            List<ValidatorLinkInfo> valLinks = validatorLinksBySource.get(filePath);
            if (valLinks != null) {
                valLinks.forEach(l -> linkedFiles.add(l.targetFile()));
            }
        }
        allFiles.addAll(linkedFiles);

        List<String> toEnrich = new ArrayList<>();
        for (String filePath : allFiles) {
            if (knowledge.semanticEnrichment().findByFilePath(filePath).isPresent()) {
                log.debug("EnrichFlowAction: using cached enrichment for {}", filePath);
                continue;
            }
            if (shouldEnrich(filePath, flow, aopLinksByTarget)) {
                toEnrich.add(filePath);
            }
        }

        if (toEnrich.isEmpty()) {
            log.debug("EnrichFlowAction: no files need enrichment for flow {}", flow.flowId());
            return;
        }

        log.info("EnrichFlowAction: enriching {} file(s) for flow {}", toEnrich.size(), flow.flowId());
        var executor = Executors.newFixedThreadPool(
            enrichmentConfig.resolvedMaxConcurrentLlmCalls());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String filePath : toEnrich) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    enrichFile(filePath);
                } catch (Exception e) {
                    log.warn("EnrichFlowAction: enrichment failed for {}: {}", filePath, e.getMessage());
                }
            }, executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        log.info("EnrichFlowAction: enriched {} file(s) for flow {}", toEnrich.size(), flow.flowId());
    }

    private void enrichFile(String filePath) throws Exception {
        String sourceContent = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        String contentHash = sha256Hex(sourceContent);

        var existingTask = taskStore.findByFilePath(filePath);
        String taskId;
        String targetName;
        if (existingTask.isPresent()) {
            Task t = existingTask.get();
            taskId = t.taskId();
            targetName = t.targetName();
        } else {
            targetName = "flow-driven";
            taskId = targetName + "|" + filePath + "|" + contentHash;
        }

        String testContent = null;
        var pairedTest = testFileMatcher.findTestFilePath(filePath);
        if (pairedTest.isPresent()) {
            var testPath = Path.of(pairedTest.get());
            if (Files.exists(testPath)) {
                testContent = Files.readString(testPath, StandardCharsets.UTF_8);
            }
        }

        String structuralContext = structuralContextAssembler.assemble(taskId);

        var task = new Task(taskId, filePath, TaskStatus.ENRICH_PENDING, "java", contentHash, targetName);
        enrichmentService.enrich(task, sourceContent, testContent, structuralContext, false).get();
    }

    private boolean shouldEnrich(String filePath, ExecutionFlow flow,
                                  Map<String, List<AopAdviceLinkInfo>> aopLinksByTarget) {
        if (executionFindingStore.existsByFilePathAndType(filePath, FindingType.DATABASE_PROCEDURE_CALL)) {
            return true;
        }
        if (executionFindingStore.existsByFilePathAndType(filePath, FindingType.CONSTRAINT_VALIDATOR)) {
            return true;
        }
        if (executionFindingStore.existsByFilePathAndType(filePath, FindingType.NATIVE_SQL_QUERY)) {
            return true;
        }
        if (executionFindingStore.existsByFilePathAndType(filePath, FindingType.JPQL_HQL_QUERY)) {
            return true;
        }

        if (hasUnresolvedFloatingLink(filePath)) {
            return true;
        }

        if (testFileMatcher.findTestFilePath(filePath).isPresent()) {
            return true;
        }

        if (aopLinksByTarget.containsKey(filePath)) {
            return true;
        }

        if (filePath.equals(flow.entryPoint().filePath())
            && assessComplexityOrdinal(flow) >= ComplexityLevel.STANDARD.ordinal()) {
            return true;
        }

        return false;
    }

    private boolean hasUnresolvedFloatingLink(String filePath) {
        return linkRegistry.floatingLinks().stream()
            .anyMatch(l -> filePath.equals(l.sourceFilePath())
                && "PENDING".equals(l.resolvedStatus()));
    }

    private Map<String, List<AopAdviceLinkInfo>> loadAopLinksByTarget() {
        Map<String, List<AopAdviceLinkInfo>> byTarget = new HashMap<>();
        var rows = executionFindingStore.findAllByType(FindingType.AOP_ADVICE_LINK);
        for (var row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                AopAdviceLinkInfo link = objectMapper.readValue(json, AopAdviceLinkInfo.class);
                byTarget.computeIfAbsent(link.targetFile(), k -> new ArrayList<>()).add(link);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize AOP_ADVICE_LINK: {}", e.getMessage());
            }
        }
        return byTarget;
    }

    private Map<String, List<ValidatorLinkInfo>> loadValidatorLinksBySource() {
        Map<String, List<ValidatorLinkInfo>> bySource = new HashMap<>();
        var rows = executionFindingStore.findAllByType(FindingType.VALIDATOR_LINK);
        for (var row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                ValidatorLinkInfo link = objectMapper.readValue(json, ValidatorLinkInfo.class);
                bySource.computeIfAbsent(link.filePath(), k -> new ArrayList<>()).add(link);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize VALIDATOR_LINK: {}", e.getMessage());
            }
        }
        return bySource;
    }

    private static int assessComplexityOrdinal(ExecutionFlow flow) {
        return assessComplexity(flow).ordinal();
    }

    private static ComplexityLevel assessComplexity(ExecutionFlow flow) {
        int stepCount = flow.steps().size();
        boolean hasExternalCalls = flow.steps().stream()
            .anyMatch(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL);
        boolean hasDatabase = flow.steps().stream()
            .anyMatch(s -> s.componentType() == FlowStepComponentType.DATABASE);

        double score = 0.0;
        score += Math.min(stepCount / 5.0, 1.0) * 0.4;
        score += (hasExternalCalls ? 0.3 : 0.0);
        score += (hasDatabase ? 0.3 : 0.0);

        if (score >= 0.7) return ComplexityLevel.FULL;
        if (score >= 0.3) return ComplexityLevel.STANDARD;
        return ComplexityLevel.MINIMAL;
    }

    private static String sha256Hex(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
