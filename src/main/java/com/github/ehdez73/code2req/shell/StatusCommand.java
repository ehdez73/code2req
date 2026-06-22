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
                         help = "Filter by status: PENDING, ENRICHING, INDEXED, ENRICHED, FAILED") String statusFilter,
            @ShellOption(value = "--verbose", defaultValue = "false",
                         help = "List individual task file paths") boolean verbose) {

        var sb = new StringBuilder("=== Task Store Status ===\n\n");

        if (statusFilter != null) {
            TaskStatus filter;
            try {
                filter = TaskStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "Error: Invalid status '" + statusFilter + "'. Valid values: PENDING, ENRICHING, INDEXED, ENRICHED, FAILED, AWAITING_HUMAN_REVIEW";
            }
            appendStatusGroup(sb, filter, verbose);
        } else {
            sb.append(String.format("  Total: %d%n", taskStore.count()));
            for (TaskStatus status : TaskStatus.values()) {
                appendStatusGroup(sb, status, verbose);
            }
        }

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
}
