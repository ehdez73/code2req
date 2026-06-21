package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.List;

@ShellComponent
public class RetryFailedCommand {

    private static final Logger log = LoggerFactory.getLogger(RetryFailedCommand.class);

    private final TaskStore taskStore;

    public RetryFailedCommand(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    @ShellMethod(key = "retry-failed", value = "Reset all FAILED tasks to SUCCESS so they are re-evaluated by the Phase 2 planner on the next run. Tasks that already have enriched findings are skipped by the planner.")
    public String retryFailed() {
        List<Task> failedTasks = taskStore.findByStatus(TaskStatus.FAILED);
        if (failedTasks.isEmpty()) {
            return "No FAILED tasks to retry.";
        }

        int count = 0;
        for (Task task : failedTasks) {
            taskStore.updateStatus(task.taskId(), TaskStatus.SUCCESS);
            count++;
            log.info("Reset task {} ({}) from FAILED to SUCCESS", task.taskId(), task.filePath());
        }

        return String.format("Reset %d FAILED task(s) to SUCCESS. Run `run` to re-enrich them.", count);
    }
}
