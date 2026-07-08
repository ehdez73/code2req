package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.extraction.ExtractionCache;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ShellComponent
public class StatusCommand {

    private static final Logger log = LoggerFactory.getLogger(StatusCommand.class);

    private final TaskStore taskStore;
    private final MetricsStore metricsStore;
    private final Path cacheFilePath;
    private final ObjectMapper objectMapper;

    public StatusCommand(TaskStore taskStore, MetricsStore metricsStore,
                         @Value("${code2req.output.spec-dir}") String specDir,
                         @Value("${code2req.output.extraction-cache-file}") String cacheFile) {
        this.taskStore = taskStore;
        this.metricsStore = metricsStore;
        this.cacheFilePath = Path.of(specDir, cacheFile).normalize();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @ShellMethod(key = "status", value = "Shows the task store summary with counts per status and Phase 2+3 metrics")
    public String status(
            @ShellOption(value = "--status", defaultValue = ShellOption.NULL,
                          help = "Filter by status: PENDING, ENRICH_PENDING, ENRICHING, INDEXED, ENRICHED, FAILED, ENRICH_FAILED") String statusFilter,
            @ShellOption(value = "--verbose", defaultValue = "false",
                         help = "List individual task file paths") boolean verbose) {

        var sb = new StringBuilder("=== Task Store Status ===\n\n");

        if (statusFilter != null) {
            TaskStatus filter;
            try {
                filter = TaskStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "Error: Invalid status '" + statusFilter + "'. Valid values: PENDING, ENRICH_PENDING, ENRICHING, INDEXED, ENRICHED, FAILED, ENRICH_FAILED, AWAITING_HUMAN_REVIEW";
            }
            appendStatusGroup(sb, filter, verbose);
        } else {
            sb.append(String.format("  Total: %d%n", taskStore.count()));
            for (TaskStatus status : TaskStatus.values()) {
                appendStatusGroup(sb, status, verbose);
            }
        }

        boolean hasFilter = statusFilter != null;

        sb.append("\n=== Phase 2 Metrics ===\n");
        Metric p2 = metricsStore.getLatestForPhase(2);
        if (p2 != null) {
            sb.append(String.format("  Tasks completed: %d%n", p2.tasksCompleted()));
            sb.append(String.format("  Tokens consumed: %d%n", p2.tokensConsumed()));
            sb.append(String.format("  Estimated cost: $%.6f%n", p2.apiCostEstimated()));
        } else {
            sb.append("  No Phase 2 run data available\n");
        }

        sb.append("\n=== Phase 3 Metrics ===\n");
        Metric p3 = metricsStore.getLatestForPhase(3);
        if (p3 != null) {
            sb.append(String.format("  Tasks completed: %d%n", p3.tasksCompleted()));
            sb.append(String.format("  Tokens consumed: %d%n", p3.tokensConsumed()));
        } else {
            sb.append("  No Phase 3 run data available\n");
        }

        if (!hasFilter) {
            appendQuarantineGaps(sb);
        }

        return sb.toString();
    }

    private void appendStatusGroup(StringBuilder sb, TaskStatus status, boolean verbose) {
        List<Task> tasks = taskStore.findByStatus(status);
        sb.append(String.format("  %s: %d  - %s%n", status, tasks.size(), status.getDescription()));
        if (verbose && !tasks.isEmpty()) {
            for (Task task : tasks) {
                sb.append(String.format("    - %s%n", task.filePath()));
            }
        }
    }

    private void appendQuarantineGaps(StringBuilder sb) {
        if (!Files.exists(cacheFilePath)) return;

        try {
            ExtractionCache cache = objectMapper.readValue(cacheFilePath.toFile(), ExtractionCache.class);
            var gaps = cache.quarantineGaps();
            if (gaps == null || gaps.isEmpty()) return;

            sb.append("\n=== Flows Flagged for Human Review ===\n\n");
            for (var gap : gaps) {
                sb.append(String.format("  Flow \"%s\" (%s)%n", gap.flowName(), gap.filePath()));
                sb.append(String.format("    Reason: %s%n", gap.reason()));
                sb.append(String.format("    Suggested: %s%n", gap.suggestedApproach()));
                sb.append(String.format("    Confidence: %.0f%%%n%n", gap.confidence() * 100));
            }
        } catch (IOException e) {
            log.debug("Could not read extraction cache at {}: {}", cacheFilePath, e.getMessage());
        }
    }
}
