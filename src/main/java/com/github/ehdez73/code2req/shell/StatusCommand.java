package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
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

    public StatusCommand(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    @ShellMethod(key = "status", value = "Shows the task store summary with counts per status")
    public String status(
            @ShellOption(value = "--status", defaultValue = ShellOption.NULL,
                         help = "Filter by status: PENDING, RUNNING, SUCCESS, FAILED") String statusFilter,
            @ShellOption(value = "--verbose", defaultValue = "false",
                         help = "List individual task file paths") boolean verbose) {

        var sb = new StringBuilder("=== Task Store Status ===\n\n");

        if (statusFilter != null) {
            TaskStatus filter;
            try {
                filter = TaskStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "Error: Invalid status '" + statusFilter + "'. Valid values: PENDING, RUNNING, SUCCESS, FAILED";
            }
            appendStatusGroup(sb, filter, verbose);
        } else {
            sb.append(String.format("  Total: %d%n", taskStore.count()));
            for (TaskStatus status : TaskStatus.values()) {
                appendStatusGroup(sb, status, verbose);
            }
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
