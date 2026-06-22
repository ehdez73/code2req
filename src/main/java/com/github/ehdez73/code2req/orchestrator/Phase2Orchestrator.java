package com.github.ehdez73.code2req.orchestrator;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.executor.ContextBudgetCalculator;
import com.github.ehdez73.code2req.executor.SemanticExecutor;
import com.github.ehdez73.code2req.model.ExecutionConfig;
import com.github.ehdez73.code2req.model.ExecutionFinding;
import com.github.ehdez73.code2req.model.Metric;
import com.github.ehdez73.code2req.model.PlannerDecision;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.model.ScanTarget;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.planner.Phase2Planner;
import com.github.ehdez73.code2req.service.FilePathResolver;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

@Service
public class Phase2Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Phase2Orchestrator.class);
    private static final double COST_PER_TOKEN = 0.000002;
    private static final int DRY_RUN_ESTIMATED_TOKENS = 500;

    private final Phase2Planner planner;
    private final SemanticExecutor semanticExecutor;
    private final TaskStore taskStore;
    private final ExecutionFindingStore findingStore;
    private final MetricsStore metricsStore;
    private final ExecutionConfig executionConfig;
    private final TaskIdHasher taskIdHasher;
    private final ContextBudgetCalculator budgetCalculator;
    private final ManifestLoader manifestLoader;
    private final FilePathResolver filePathResolver;

    public Phase2Orchestrator(Phase2Planner planner,
                              SemanticExecutor semanticExecutor,
                              TaskStore taskStore,
                              ExecutionFindingStore findingStore,
                              MetricsStore metricsStore,
                              ExecutionConfig executionConfig,
                              TaskIdHasher taskIdHasher,
                              ContextBudgetCalculator budgetCalculator,
                              ManifestLoader manifestLoader,
                              FilePathResolver filePathResolver) {
        this.planner = planner;
        this.semanticExecutor = semanticExecutor;
        this.taskStore = taskStore;
        this.findingStore = findingStore;
        this.metricsStore = metricsStore;
        this.executionConfig = executionConfig;
        this.taskIdHasher = taskIdHasher;
        this.budgetCalculator = budgetCalculator;
        this.manifestLoader = manifestLoader;
        this.filePathResolver = filePathResolver;
    }

    public CompletionStatus executePhase2(String manifestPath, boolean dryRun) {
        Collection<ScanTarget> targets = loadScanTargets(manifestPath);

        List<PlannerDecision> decisions = planner.plan();
        List<PlannerDecision> qualified = decisions.stream()
            .filter(PlannerDecision::qualified)
            .toList();

        if (qualified.isEmpty()) {
            log.info("No qualified tasks for Phase 2 enrichment");
            return new CompletionStatus(0, 0, 0, 0, 0, 0.0, List.of());
        }

        log.info("Phase 2 orchestrator starting with {} qualified tasks", qualified.size());

        int maxDepth = executionConfig.maxDiscoveryDepth();
        EnrichmentDag dag = new EnrichmentDag(maxDepth);
        for (PlannerDecision d : qualified) {
            dag.registerRootTask(d);
        }

        int totalTokens = 0;
        int tasksCompleted = 0;
        int tasksFailed = 0;
        int depsDiscovered = 0;
        int depsSkipped = 0;
        List<String> awaitingReview = new ArrayList<>();
        List<PlannerDecision> toSubmit = new ArrayList<>(qualified);

        while (!toSubmit.isEmpty()) {
            List<SubmitEntry> batch = new ArrayList<>();

            for (PlannerDecision decision : toSubmit) {
                String hashKey = hashKey(decision);
                if (dag.isVisited(hashKey)) {
                    continue;
                }
                dag.markVisited(hashKey);

                Optional<Task> taskOpt = taskStore.findById(decision.taskId());
                Task task;
                if (taskOpt.isEmpty()) {
                    task = new Task(decision.taskId(), decision.filePath(),
                        TaskStatus.PENDING, "java", hashKey, decision.targetName());
                    taskStore.save(task);
                } else {
                    task = taskOpt.get();
                }

                String resolvedPath = resolveTaskFilePath(task.filePath(), targets);
                if (resolvedPath == null) {
                    log.warn("Skipping task {} — file path '{}' does not exist under any scan target",
                        task.taskId(), task.filePath());
                    taskStore.updateStatus(task.taskId(), TaskStatus.FAILED);
                    tasksFailed++;
                    continue;
                }

                String sourceContent = readFileContent(resolvedPath);
                taskStore.updateStatus(task.taskId(), TaskStatus.ENRICHING);

                CompletableFuture<ExecutionFinding> future = semanticExecutor.enrich(
                    task, decision, sourceContent, null, null, dryRun);

                batch.add(new SubmitEntry(decision, task, future));
            }

            if (batch.isEmpty()) {
                break;
            }

            CompletableFuture<?>[] futuresArray = batch.stream()
                .map(e -> e.future)
                .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futuresArray).join();

            List<PlannerDecision> nextBatch = new ArrayList<>();
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

                    if (result.discoveredDependencies() != null
                        && !result.discoveredDependencies().isEmpty()) {

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

                            String childTaskId = dag.registerDiscoveredDependency(
                                entry.decision.taskId(), resolvedDepPath);
                            if (childTaskId == null) {
                                childTaskId = taskIdHasher.hash(resolvedDepPath, depHash, depTargetName);
                            }

                            String taskHash = sha256(resolvedDepPath + "|" + System.nanoTime());
                            Task newTask = new Task(childTaskId, resolvedDepPath,
                                TaskStatus.PENDING, entry.task.contentType(), taskHash, depTargetName);
                            taskStore.save(newTask);

                            PlannerDecision newDecision = PlannerDecision.qualified(
                                childTaskId, resolvedDepPath, depTargetName, entry.decision.reasons());
                            dag.enqueue(newDecision);
                            nextBatch.add(newDecision);
                        }
                    }
                } catch (Exception e) {
                    tasksFailed++;
                    log.error("Task {} failed: {}", entry.decision.taskId(), e.getMessage());
                }
            }

            for (SubmitEntry entry : batch) {
                dag.markTaskComplete(entry.decision.taskId());
            }

            toSubmit = nextBatch;
        }

        double costEstimate = totalTokens * COST_PER_TOKEN;
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
        return decision.taskId() + "|" + decision.filePath();
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
}
