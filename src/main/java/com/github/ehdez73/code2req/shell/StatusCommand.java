package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.model.Metric;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

@ShellComponent
public class StatusCommand {

    private static final Logger log = LoggerFactory.getLogger(StatusCommand.class);

    private final TaskStore taskStore;
    private final MetricsStore metricsStore;

    public StatusCommand(TaskStore taskStore, MetricsStore metricsStore) {
        this.taskStore = taskStore;
        this.metricsStore = metricsStore;
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

        int pendingCount = taskStore.countByStatus(TaskStatus.PENDING);
        int enrichPendingCount = taskStore.countByStatus(TaskStatus.ENRICH_PENDING);
        int enrichingCount = taskStore.countByStatus(TaskStatus.ENRICHING);
        int indexedCount = taskStore.countByStatus(TaskStatus.INDEXED);
        int enrichedCount = taskStore.countByStatus(TaskStatus.ENRICHED);
        int failedCount = taskStore.countByStatus(TaskStatus.FAILED);
        int enrichFailedCount = taskStore.countByStatus(TaskStatus.ENRICH_FAILED);

        if (hasFilter) {
            return sb.toString();
        }

        int total = taskStore.count();
        if (total == 0) {
            sb.append("\n=== Suggested Next Steps ===\n");
            sb.append("  No tasks found. Start by scanning a project:\n");
            sb.append("    scan\n");
        } else {
            appendSuggestions(sb, pendingCount, enrichPendingCount, enrichingCount,
                indexedCount, enrichedCount, failedCount, enrichFailedCount, p3);
        }

        return sb.toString();
    }

    private void appendStatusGroup(StringBuilder sb, TaskStatus status, boolean verbose) {
        List<Task> tasks = taskStore.findByStatus(status);
        sb.append(String.format("  %s: %d%n", status, tasks.size()));
        if (verbose && !tasks.isEmpty()) {
            for (Task task : tasks) {
                sb.append(String.format("    - %s%n", task.filePath()));
            }
        }
    }

    private void appendSuggestions(StringBuilder sb, int pendingCount, int enrichPendingCount,
                                    int enrichingCount, int indexedCount, int enrichedCount,
                                    int failedCount, int enrichFailedCount, Metric p3) {
        sb.append("\n=== Suggested Next Steps ===\n");

        boolean hasPhase1Failures = failedCount > 0;
        boolean hasPhase2Failures = enrichFailedCount > 0;
        boolean hasEnriching = enrichingCount > 0;
        boolean hasPending = pendingCount > 0;
        boolean hasReady = enrichPendingCount > 0;
        boolean hasIndexed = indexedCount > 0;

        if (hasPhase1Failures) {
            sb.append("  ").append(failedCount).append(" FAILED task(s) — file unreadable during Phase 1:\n");
            sb.append("    scan --resume\n");
        }

        if (hasPhase2Failures || hasEnriching || hasPending) {
            String detail = (hasPhase2Failures ? "ENRICH_FAILED=" + enrichFailedCount + ", " : "")
                + (hasEnriching ? "ENRICHING=" + enrichingCount + ", " : "")
                + (hasPending ? "PENDING=" + pendingCount : "");
            if (detail.endsWith(", ")) detail = detail.substring(0, detail.length() - 2);
            sb.append("  Tasks need recovery (").append(detail).append("):\n");
            sb.append("    run --resume\n");
        }

        if (hasIndexed) {
            sb.append("  ").append(indexedCount).append(" INDEXED task(s) ready for qualification:\n");
            sb.append("    plan\n");
        }

        if (hasReady && !hasEnriching && !hasPending && !hasPhase2Failures) {
            sb.append("  ").append(enrichPendingCount).append(" task(s) waiting for enrichment:\n");
            sb.append("    run\n");
        }

        boolean allEnriched = !hasPhase1Failures && !hasPhase2Failures && !hasEnriching
            && !hasPending && !hasIndexed && !hasReady && enrichedCount > 0;
        if (allEnriched && p3 == null) {
            sb.append("  All tasks enriched. Proceed to Phase 3 extraction:\n");
            sb.append("    run\n");
        }

        if (!hasPhase1Failures && !hasPhase2Failures && !hasEnriching && !hasPending
            && !hasIndexed && !hasReady && !allEnriched) {
            sb.append("  No actionable tasks. Nothing to do.\n");
        }
    }
}
