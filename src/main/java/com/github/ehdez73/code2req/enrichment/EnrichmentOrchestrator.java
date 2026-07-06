package com.github.ehdez73.code2req.enrichment;

import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.common.domain.ScanTarget;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.enrichment.adapter.llm.ContextBudgetCalculator;
import com.github.ehdez73.code2req.enrichment.adapter.llm.LlmEnrichmentService;
import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.PairedExecutionResolver;
import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.PairedExecutionResolver.PairedTestInfo;
import com.github.ehdez73.code2req.enrichment.domain.model.CompletionStatus;
import com.github.ehdez73.code2req.indexing.domain.model.IndexingConfig;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.enrichment.domain.model.PlannerDecision;
import com.github.ehdez73.code2req.enrichment.domain.planner.EnrichmentPlanner;
import com.github.ehdez73.code2req.enrichment.domain.service.BranchState;
import com.github.ehdez73.code2req.enrichment.domain.service.EnrichmentDag;
import com.github.ehdez73.code2req.infrastructure.config.ManifestLoader;
import com.github.ehdez73.code2req.infrastructure.file.FilePathResolver;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskIdHasher;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class EnrichmentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentOrchestrator.class);
    private static final double COST_PER_TOKEN = 0.000002;
    private static final int DRY_RUN_ESTIMATED_TOKENS = 500;

    private final EnrichmentPlanner planner;
    private final LlmEnrichmentService semanticExecutor;
    private final TaskStore taskStore;
    private final MetricsStore metricsStore;
    private final IndexingConfig indexingConfig;
    private final TaskIdHasher taskIdHasher;
    private final ContextBudgetCalculator budgetCalculator;
    private final ManifestLoader manifestLoader;
    private final FilePathResolver filePathResolver;
    private final PairedExecutionResolver pairedExecutionResolver;

    public EnrichmentOrchestrator(EnrichmentPlanner planner, LlmEnrichmentService executor,
                                  TaskStore taskStore, MetricsStore metricsStore, IndexingConfig indexingConfig,
                                  TaskIdHasher taskIdHasher, ContextBudgetCalculator budgetCalculator,
                                  ManifestLoader manifestLoader, FilePathResolver filePathResolver,
                                  PairedExecutionResolver per) {
        this.planner = planner;
        this.semanticExecutor = executor;
        this.taskStore = taskStore;
        this.metricsStore = metricsStore;
        this.indexingConfig = indexingConfig;
        this.taskIdHasher = taskIdHasher;
        this.budgetCalculator = budgetCalculator;
        this.manifestLoader = manifestLoader;
        this.filePathResolver = filePathResolver;
        this.pairedExecutionResolver = per;
    }

    public CompletionStatus execute(String manifestPath, boolean dryRun) {
        Collection<ScanTarget> targets = loadScanTargets(manifestPath);
        List<PlannerDecision> qualified = getQualifiedDecisions();
        if (qualified.isEmpty()) {
            return emptyCompletionStatus();
        }

        EnrichmentDag dag = initializeDag(qualified);
        int maxDepth = indexingConfig.resolvedMaxDiscoveryDepth();

        int totalTokens = 0;
        int tasksCompleted = 0;
        int tasksFailed = 0;
        int depsDiscovered = 0;
        int depsSkipped = 0;
        List<String> awaitingReview = new ArrayList<>();
        List<PlannerDecision> toSubmit = new ArrayList<>(qualified);

        while (!toSubmit.isEmpty()) {
            BuildBatchResult buildResult = buildSubmitBatch(toSubmit, dag, targets, dryRun);
            if (buildResult.batch().isEmpty()) {
                break;
            }
            tasksFailed += buildResult.resolutionFailures();

            waitForAll(buildResult.batch());

            BatchResult result = processCompletedBatch(buildResult.batch(), dag, maxDepth, dryRun, targets);
            tasksCompleted += result.tasksCompleted();
            tasksFailed += result.tasksFailed();
            totalTokens += result.totalTokens();
            depsDiscovered += result.depsDiscovered();
            depsSkipped += result.depsSkipped();
            awaitingReview.addAll(result.awaitingReview());
            toSubmit = result.nextBatch();
        }

        double costEstimate = totalTokens * COST_PER_TOKEN;
        buildAndSaveMetric(tasksCompleted, tasksFailed, totalTokens, costEstimate);

        log.info("Phase 2 complete: {} tasks completed, {} failed, {} deps discovered, {} deps skipped (external/unresolvable), {} tokens consumed, ${} estimated",
            tasksCompleted, tasksFailed, depsDiscovered, depsSkipped, totalTokens, String.format("%.6f", costEstimate));

        return new CompletionStatus(
            qualified.size() + depsDiscovered,
            tasksCompleted,
            tasksFailed,
            depsDiscovered,
            totalTokens,
            costEstimate,
            awaitingReview
        );
    }

    private List<PlannerDecision> getQualifiedDecisions() {
        List<PlannerDecision> decisions = planner.plan();
        List<PlannerDecision> qualified = decisions.stream()
            .filter(PlannerDecision::qualified)
            .toList();
        if (qualified.isEmpty()) {
            log.info("No qualified tasks for Phase 2 enrichment");
        } else {
            log.info("Phase 2 orchestrator starting with {} qualified tasks", qualified.size());
        }
        return qualified;
    }

    private CompletionStatus emptyCompletionStatus() {
        return new CompletionStatus(0, 0, 0, 0, 0, 0.0, List.of());
    }

    private EnrichmentDag initializeDag(List<PlannerDecision> qualified) {
        int maxDepth = indexingConfig.resolvedMaxDiscoveryDepth();
        EnrichmentDag dag = new EnrichmentDag(maxDepth);
        for (PlannerDecision d : qualified) {
            dag.registerRootTask(d);
        }
        return dag;
    }

    private BuildBatchResult buildSubmitBatch(List<PlannerDecision> decisions,
                                              EnrichmentDag dag,
                                              Collection<ScanTarget> targets,
                                              boolean dryRun) {
        List<SubmitEntry> batch = new ArrayList<>();
        int resolutionFailures = 0;

        for (PlannerDecision decision : decisions) {
            String hashKey = hashKey(decision);
            if (dag.isVisited(hashKey)) {
                log.debug("Skipping task {} — file '{}' already processed in this run", decision.taskId(), decision.filePath());
                continue;
            }
            dag.markVisited(hashKey);

            Optional<Task> taskOpt = taskStore.findById(decision.taskId());
            Task task;
            if (taskOpt.isEmpty()) {
                String earlyResolvedPath = resolveTaskFilePath(decision.filePath(), targets);
                String earlyPairedPath = null;
                if (earlyResolvedPath != null) {
                    earlyPairedPath = resolveTestInfo(earlyResolvedPath, decision)
                        .map(PairedTestInfo::testFilePath)
                        .orElse(null);
                }
                task = new Task(decision.taskId(), decision.filePath(),
                    TaskStatus.PENDING, "java", hashKey, decision.targetName(), earlyPairedPath);
                taskStore.save(task);
            } else {
                task = taskOpt.get();
            }

            String resolvedPath = resolveTaskFilePath(task.filePath(), targets);
            if (resolvedPath == null) {
                log.warn("Skipping task {} — file path '{}' does not exist under any scan target",
                    task.taskId(), task.filePath());
                taskStore.updateStatus(task.taskId(), TaskStatus.ENRICH_FAILED);
                resolutionFailures++;
                continue;
            }

            String sourceContent = readFileContent(resolvedPath);
            taskStore.updateStatus(task.taskId(), TaskStatus.ENRICHING);

            String testContent = resolveTestContent(resolvedPath, decision);

            CompletableFuture<ExecutionFinding> future = semanticExecutor.enrich(
                task, decision, sourceContent, testContent, null, dryRun);

            batch.add(new SubmitEntry(decision, task, future));
        }

        return new BuildBatchResult(batch, resolutionFailures);
    }

    private void waitForAll(List<SubmitEntry> batch) {
        CompletableFuture<?>[] futuresArray = batch.stream()
            .map(e -> e.future)
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futuresArray).join();
    }

    private BatchResult processCompletedBatch(List<SubmitEntry> batch,
                                              EnrichmentDag dag,
                                              int maxDepth,
                                              boolean dryRun,
                                              Collection<ScanTarget> targets) {
        List<PlannerDecision> nextBatch = new ArrayList<>();
        int tasksCompleted = 0;
        int tasksFailed = 0;
        int totalTokens = 0;
        int depsDiscovered = 0;
        int depsSkipped = 0;
        List<String> awaitingReview = new ArrayList<>();

        for (SubmitEntry entry : batch) {
            try {
                ExecutionFinding result = entry.future.get();
                tasksCompleted++;

                if (dryRun) {
                    totalTokens += DRY_RUN_ESTIMATED_TOKENS;
                } else {
                    String content = result.businessAbstraction() != null
                        ? result.businessAbstraction().toString() : "";
                    totalTokens += budgetCalculator.estimateTokenCount(content);
                }

                DependencyResult depResult = processDiscoveredDependencies(result, entry, dag, maxDepth, targets, nextBatch);
                depsDiscovered += depResult.depsDiscovered();
                depsSkipped += depResult.depsSkipped();
                awaitingReview.addAll(depResult.awaitingReview());
            } catch (Exception e) {
                tasksFailed++;
                log.error("Task {} failed: {}", entry.decision.taskId(), e.getMessage());
            }
        }

        for (SubmitEntry entry : batch) {
            dag.markTaskComplete(entry.decision.taskId());
        }

        return new BatchResult(nextBatch, tasksCompleted, tasksFailed, totalTokens,
            depsDiscovered, depsSkipped, awaitingReview);
    }

    private DependencyResult processDiscoveredDependencies(ExecutionFinding result,
                                                           SubmitEntry entry,
                                                           EnrichmentDag dag,
                                                           int maxDepth,
                                                           Collection<ScanTarget> targets,
                                                           List<PlannerDecision> nextBatch) {
        if (result.discoveredDependencies() == null || result.discoveredDependencies().isEmpty()) {
            return new DependencyResult(0, 0, List.of());
        }

        int depsDiscovered = 0;
        int depsSkipped = 0;
        List<String> awaitingReview = new ArrayList<>();

        for (ExecutionFinding.DiscoveredDependency dep : result.discoveredDependencies()) {
            Optional<Path> resolved = filePathResolver.resolve(dep.filePath(), targets);
            if (resolved.isEmpty()) {
                log.info("Skipping discovered dependency '{}' from task {} — not resolvable under any scan target (likely external library)",
                    dep.filePath(), entry.decision.taskId());
                depsSkipped++;
                continue;
            }

            String resolvedDepPath = resolved.get().toString();
            String depHash = sha256(resolvedDepPath);

            if (dag.isVisited(depHash)) {
                log.debug("Discovered dependency {} already visited, skipping", resolvedDepPath);
                continue;
            }
            dag.markVisited(depHash);
            depsDiscovered++;

            String depTargetName = entry.task.targetName();
            BranchState branch = dag.getBranch(entry.decision.taskId());

            if (branch != null && branch.incrementDepth()) {
                log.warn("Max hop depth exceeded for dependency {} (depth={}, max={})",
                    resolvedDepPath, branch.currentDepth(), maxDepth);
                String depTaskId = taskIdHasher.hash(resolvedDepPath, depHash, depTargetName);
                taskStore.updateStatus(depTaskId, TaskStatus.AWAITING_HUMAN_REVIEW);
                awaitingReview.add(resolvedDepPath);
                continue;
            }

            String childTaskId = dag.registerDiscoveredDependency(entry.decision.taskId(), resolvedDepPath);
            if (childTaskId == null) {
                childTaskId = taskIdHasher.hash(resolvedDepPath, depHash, depTargetName);
            }

            String depPairedPath = resolveTestInfo(resolvedDepPath, entry.decision)
                .map(PairedTestInfo::testFilePath)
                .orElse(null);
            String taskHash = sha256(resolvedDepPath + "|" + System.nanoTime());
            Task newTask = new Task(childTaskId, resolvedDepPath,
                TaskStatus.PENDING, entry.task.contentType(), taskHash, depTargetName, depPairedPath);
            taskStore.save(newTask);

            PlannerDecision newDecision = PlannerDecision.qualified(
                childTaskId, resolvedDepPath, depTargetName, entry.decision.reasons());
            dag.enqueue(newDecision);
            nextBatch.add(newDecision);
        }

        return new DependencyResult(depsDiscovered, depsSkipped, awaitingReview);
    }

    private void buildAndSaveMetric(int tasksCompleted, int tasksFailed, int totalTokens, double costEstimate) {
        Metric metric = new Metric(
            UUID.randomUUID().toString(),
            2,
            tasksCompleted + tasksFailed,
            tasksCompleted,
            0, 0, 0, 0,
            totalTokens,
            costEstimate,
            LocalDateTime.now().toString()
        );
        metricsStore.save(metric);
    }

    private Collection<ScanTarget> loadScanTargets(String manifestPath) {
        try {
            ProjectManifest manifest = manifestLoader.load(Path.of(manifestPath));
            return manifest.targets();
        } catch (IOException e) {
            log.warn("Could not load manifest from {}: {} — dependency resolution will be limited", manifestPath, e.getMessage());
            return List.of();
        }
    }

    private String resolveTaskFilePath(String filePath, Collection<ScanTarget> targets) {
        Path p = Path.of(filePath);
        if (Files.isRegularFile(p)) {
            return p.toAbsolutePath().normalize().toString();
        }
        if (!targets.isEmpty()) {
            Optional<Path> resolved = filePathResolver.resolve(filePath, targets);
            if (resolved.isPresent()) {
                return resolved.get().toString();
            }
            log.debug("File '{}' not found at direct path and not resolved against scan targets — will use as-is", filePath);
        }
        return filePath;
    }

    private Optional<PairedTestInfo> resolveTestInfo(String sourceFilePath, PlannerDecision decision) {
        try {
            return pairedExecutionResolver.resolve(sourceFilePath);
        } catch (Exception e) {
            log.warn("Failed to resolve test info for {}: {}", sourceFilePath, e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveTestContent(String sourceFilePath, PlannerDecision decision) {
        return resolveTestInfo(sourceFilePath, decision)
            .map(PairedTestInfo::testContent)
            .orElse(null);
    }

    private String readFileContent(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            return Files.readString(Path.of(filePath));
        } catch (IOException e) {
            log.warn("Could not read file {}: {}", filePath, e.getMessage());
            return "";
        }
    }

    private String hashKey(PlannerDecision decision) {
        return decision.taskId();
    }

    private String sha256(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return input;
        }
    }

    private record SubmitEntry(PlannerDecision decision, Task task, CompletableFuture<ExecutionFinding> future) {}
    private record BuildBatchResult(List<SubmitEntry> batch, int resolutionFailures) {}
    private record BatchResult(
        List<PlannerDecision> nextBatch,
        int tasksCompleted,
        int tasksFailed,
        int totalTokens,
        int depsDiscovered,
        int depsSkipped,
        List<String> awaitingReview
    ) {}
    private record DependencyResult(int depsDiscovered, int depsSkipped, List<String> awaitingReview) {}
}
