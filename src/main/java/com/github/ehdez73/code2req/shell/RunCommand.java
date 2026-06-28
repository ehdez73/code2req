package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.orchestrator.CompletionStatus;
import com.github.ehdez73.code2req.orchestrator.Phase2Orchestrator;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import com.github.ehdez73.code2req.synthesis.Phase3Orchestrator;
import com.github.ehdez73.code2req.synthesis.Phase3Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ShellComponent
public class RunCommand {

    private static final Logger log = LoggerFactory.getLogger(RunCommand.class);

    private final Phase2Orchestrator phase2Orchestrator;
    private final Phase3Orchestrator phase3Orchestrator;
    private final ManifestValidator manifestValidator;
    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final TopicLinkStore topicLinkStore;
    private final FloatingLinkStore floatingLinkStore;

    public RunCommand(Phase2Orchestrator phase2Orchestrator,
                      Phase3Orchestrator phase3Orchestrator,
                      ManifestValidator manifestValidator,
                      TaskStore taskStore,
                      ExecutionFindingStore executionFindingStore,
                      TopicLinkStore topicLinkStore,
                      FloatingLinkStore floatingLinkStore) {
        this.phase2Orchestrator = phase2Orchestrator;
        this.phase3Orchestrator = phase3Orchestrator;
        this.manifestValidator = manifestValidator;
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.topicLinkStore = topicLinkStore;
        this.floatingLinkStore = floatingLinkStore;
    }

    @ShellMethod(key = "run", value = "Executes all 3 phases end-to-end: indexing (if needed), Phase 2 semantic enrichment, Phase 3 functional extraction")
    public String run(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Simulation mode: stubs instead of LLM calls") boolean dryRun,
            @ShellOption(value = "--resume", defaultValue = "false",
                          help = "Recover orphaned tasks (ENRICH_FAILED, ENRICHING, ENRICH_PENDING, PENDING) before Phase 2 (use after interrupted run). ENRICH_PENDING orphans self-heal automatically.") boolean resume,
            @ShellOption(value = "--llm-threshold", defaultValue = ShellOption.NULL,
                         help = "Override unresolved signatures threshold (0 to skip Phase 2)") Integer llmThreshold,
            @ShellOption(value = "--force-phase3", defaultValue = "false",
                         help = "Force Phase 3 re-execution even if no new enrichments") boolean forcePhase3) {

        var sb = new StringBuilder("=== Run Pipeline ===\n\n");
        var start = Instant.now();

        Path manifestFile = Path.of(manifestPath);
        if (!validateManifest(manifestFile, sb)) {
            return sb.toString();
        }

        if (resume) {
            recoverOrphanedTasks(sb);
        }

        boolean skipPhase2 = (llmThreshold != null && llmThreshold == 0);
        CompletionStatus phase2Status = runPhase2(manifestPath, dryRun, skipPhase2, sb);
        runPhase3(phase2Status, dryRun, forcePhase3, sb);

        long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
        sb.append(String.format("=== Run Complete (%ds) ===%n", totalElapsed));
        if (dryRun) {
            sb.append("  Dry-run mode: no API calls made, no credentials required.\n");
        }
        if (resume) {
            sb.append("  Resume mode: orphaned tasks were recovered.\n");
        }
        if (forcePhase3) {
            sb.append("  Force mode: Phase 3 re-executed.\n");
        }

        return sb.toString();
    }

    private boolean validateManifest(Path manifestFile, StringBuilder sb) {
        if (!Files.exists(manifestFile)) {
            sb.append("Error: Manifest file not found: ").append(manifestFile);
            return false;
        }
        try {
            var validation = manifestValidator.validate(manifestFile);
            if (validation.hasErrors()) {
                sb.append("Manifest validation FAILED:\n");
                for (var err : validation.getErrors()) {
                    sb.append("  - ").append(err).append("\n");
                }
                return false;
            }
            return true;
        } catch (IOException e) {
            sb.append("Error: Failed to read manifest: ").append(e.getMessage());
            return false;
        }
    }

    private void recoverOrphanedTasks(StringBuilder sb) {
        sb.append("=== Phase 2 Orphan Recovery ===\n");
        int total = 0;

        List<Task> failed = taskStore.findByStatus(TaskStatus.FAILED);
        for (Task task : failed) {
            boolean hasFindings = executionFindingStore.countByTaskId(task.taskId()) > 0;
            if (hasFindings) {
                taskStore.updateStatus(task.taskId(), TaskStatus.ENRICHED);
                log.info("Recovered failed task {} ({}) from FAILED to ENRICHED (findings exist)", task.taskId(), task.filePath());
            } else {
                taskStore.updateStatus(task.taskId(), TaskStatus.INDEXED);
                log.info("Recovered failed task {} ({}) from FAILED to INDEXED (no findings)", task.taskId(), task.filePath());
            }
            total++;
        }

        List<Task> enriching = taskStore.findByStatus(TaskStatus.ENRICHING);
        for (Task task : enriching) {
            executionFindingStore.deleteByTaskId(task.taskId());
            topicLinkStore.deleteByTaskId(task.taskId());
            floatingLinkStore.deleteByTaskId(task.taskId());
            taskStore.updateStatus(task.taskId(), TaskStatus.ENRICH_PENDING);
            total++;
            log.info("Recovered orphaned task {} ({}) from ENRICHING to ENRICH_PENDING", task.taskId(), task.filePath());
        }

        List<Task> enrichPending = taskStore.findByStatus(TaskStatus.ENRICH_PENDING);
        for (Task task : enrichPending) {
            executionFindingStore.deleteByTaskId(task.taskId());
            topicLinkStore.deleteByTaskId(task.taskId());
            floatingLinkStore.deleteByTaskId(task.taskId());
            taskStore.updateStatus(task.taskId(), TaskStatus.INDEXED);
            total++;
            log.info("Recovered orphaned task {} ({}) from ENRICH_PENDING to INDEXED", task.taskId(), task.filePath());
        }

        List<Task> pending = taskStore.findByStatus(TaskStatus.PENDING);
        for (Task task : pending) {
            taskStore.updateStatus(task.taskId(), TaskStatus.INDEXED);
            total++;
            log.info("Recovered orphaned dependency task {} ({}) from PENDING to INDEXED", task.taskId(), task.filePath());
        }

        List<Task> enrichFailed = taskStore.findByStatus(TaskStatus.ENRICH_FAILED);
        for (Task task : enrichFailed) {
            executionFindingStore.deleteByTaskId(task.taskId());
            topicLinkStore.deleteByTaskId(task.taskId());
            floatingLinkStore.deleteByTaskId(task.taskId());
            taskStore.updateStatus(task.taskId(), TaskStatus.ENRICH_PENDING);
            total++;
            log.info("Recovered enrichment-failed task {} ({}) from ENRICH_FAILED to ENRICH_PENDING", task.taskId(), task.filePath());
        }

        int staleCleaned = executionFindingStore.deleteOrphanedSemanticEnrichment();
        if (staleCleaned > 0) {
            log.info("Cleaned {} stale SEMANTIC_ENRICHMENT finding(s) from INDEXED tasks", staleCleaned);
        }

        sb.append(String.format("  Recovered: %d FAILED, %d ENRICHING, %d ENRICH_PENDING, %d ENRICH_FAILED, %d PENDING; cleaned %d stale finding(s)%n%n",
            failed.size(), enriching.size(), enrichPending.size(), enrichFailed.size(), pending.size(), staleCleaned));
    }

    private CompletionStatus runPhase2(String manifestPath, boolean dryRun, boolean skipPhase2, StringBuilder sb) {
        if (skipPhase2) {
            sb.append("--llm-threshold is 0: skipping Phase 2 entirely\n\n");
            return new CompletionStatus(0, 0, 0, 0, 0, 0.0, List.of());
        }

        sb.append("=== Phase 2: Semantic Enrichment ===\n");
        var phase2Start = Instant.now();

        if (dryRun) {
            sb.append("  Mode: DRY RUN (simulation stubs, no API calls)\n");
        }

        CompletionStatus status = phase2Orchestrator.execute(manifestPath, dryRun);
        long p2Elapsed = Duration.between(phase2Start, Instant.now()).toSeconds();

        appendPhase2Summary(sb, status);
        sb.append(String.format("  Phase 2 elapsed: %ds%n%n", p2Elapsed));

        return status;
    }

    private void appendPhase2Summary(StringBuilder sb, CompletionStatus status) {
        sb.append(String.format("  Submitted: %d%n", status.tasksSubmitted()));
        sb.append(String.format("  Completed: %d%n", status.tasksCompleted()));
        sb.append(String.format("  Failed: %d%n", status.tasksFailed()));
        sb.append(String.format("  Dependencies discovered: %d%n", status.dependenciesDiscovered()));
        sb.append(String.format("  Tokens consumed: %d%n", status.tokensConsumed()));
        sb.append(String.format("  Estimated cost: $%.6f%n", status.apiCostEstimated()));
        if (!status.awaitingHumanReview().isEmpty()) {
            sb.append("  Awaiting human review:\n");
            for (String path : status.awaitingHumanReview()) {
                sb.append(String.format("    - %s%n", path));
            }
        }
    }

    private void runPhase3(CompletionStatus phase2Status, boolean dryRun, boolean forcePhase3, StringBuilder sb) {
        sb.append("=== Phase 3: Functional Requirement Extraction ===\n");
        var phase3Start = Instant.now();

        if (dryRun) {
            sb.append("  Mode: DRY RUN (simulation, no actual synthesis)\n");
        }
        if (forcePhase3) {
            sb.append("  Mode: FORCE (re-executing Phase 3)\n");
        }

        Phase3Result result = phase3Orchestrator.execute(phase2Status, dryRun, forcePhase3);
        long p3Elapsed = Duration.between(phase3Start, Instant.now()).toSeconds();

        sb.append(String.format("  Flows extracted: %d%n", result.flowsExtracted()));
        sb.append(String.format("  Ambiguity gaps: %d%n", result.ambiguityGaps()));
        sb.append(String.format("  Awaiting review: %d%n", result.awaitingReview()));
        sb.append(String.format("  Phase 3 elapsed: %ds%n%n", p3Elapsed));
    }
}
