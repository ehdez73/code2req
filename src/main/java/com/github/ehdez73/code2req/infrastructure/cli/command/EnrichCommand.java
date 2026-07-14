package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.infrastructure.config.ManifestValidator;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.enrichment.domain.model.CompletionStatus;
import com.github.ehdez73.code2req.enrichment.EnrichmentOrchestrator;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
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
public class EnrichCommand {

    private static final Logger log = LoggerFactory.getLogger(EnrichCommand.class);

    private final EnrichmentOrchestrator enrichmentOrchestrator;
    private final ManifestValidator manifestValidator;
    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final TopicLinkStore topicLinkStore;
    private final FloatingLinkStore floatingLinkStore;

    public EnrichCommand(EnrichmentOrchestrator enrichmentOrchestrator,
                         ManifestValidator manifestValidator,
                         TaskStore taskStore,
                         ExecutionFindingStore executionFindingStore,
                         TopicLinkStore topicLinkStore,
                         FloatingLinkStore floatingLinkStore) {
        this.enrichmentOrchestrator = enrichmentOrchestrator;
        this.manifestValidator = manifestValidator;
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.topicLinkStore = topicLinkStore;
        this.floatingLinkStore = floatingLinkStore;
    }

    @ShellMethod(key = "enrich", value = "Run Phase 2 semantic enrichment on qualified (ENRICH_PENDING) tasks")
    public String enrich(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Simulation mode: stubs instead of LLM calls") boolean dryRun,
            @ShellOption(value = "--resume", defaultValue = "false",
                          help = "Recover orphaned tasks (ENRICH_FAILED, ENRICHING, ENRICH_PENDING, PENDING) before enrichment. ENRICH_PENDING orphans self-heal automatically.") boolean resume,
            @ShellOption(value = "--llm-threshold", defaultValue = ShellOption.NULL,
                         help = "Override unresolved signatures threshold (0 to skip enrichment)") Integer llmThreshold) {

        var sb = new StringBuilder("=== Enrich ===\n\n");
        var start = Instant.now();

        Path manifestFile = Path.of(manifestPath);
        if (!validateManifest(manifestFile, sb)) {
            return sb.toString();
        }

        if (resume) {
            recoverOrphanedTasks(sb);
        }

        boolean skipPhase2 = (llmThreshold != null && llmThreshold == 0);
        CompletionStatus status = runPhase2(manifestPath, dryRun, skipPhase2, sb);

        long totalElapsed = Duration.between(start, Instant.now()).toSeconds();
        sb.append(String.format("=== Enrich Complete (%ds) ===%n", totalElapsed));
        if (dryRun) {
            sb.append("  Dry-run mode: no API calls made, no credentials required.\n");
        }
        if (resume) {
            sb.append("  Resume mode: orphaned tasks were recovered.\n");
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
        sb.append("=== Orphan Recovery ===\n");
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
            sb.append("--llm-threshold is 0: skipping enrichment entirely\n\n");
            return new CompletionStatus(0, 0, 0, 0, 0, 0.0, List.of());
        }

        sb.append("=== Phase 2: Semantic Enrichment ===\n");
        var phase2Start = Instant.now();

        if (dryRun) {
            sb.append("  Mode: DRY RUN (simulation stubs, no API calls)\n");
        }

        CompletionStatus status = enrichmentOrchestrator.execute(manifestPath, dryRun);
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
}
