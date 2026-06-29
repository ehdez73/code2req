package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TopicLinkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ShellComponent
public class TaskCommands {

    private static final Logger log = LoggerFactory.getLogger(TaskCommands.class);

    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final TopicLinkStore topicLinkStore;
    private final FloatingLinkStore floatingLinkStore;

    public TaskCommands(TaskStore taskStore, ExecutionFindingStore executionFindingStore,
                        TopicLinkStore topicLinkStore, FloatingLinkStore floatingLinkStore) {
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.topicLinkStore = topicLinkStore;
        this.floatingLinkStore = floatingLinkStore;
    }

    @ShellMethod(key = "task list", value = "List all tasks with truncated ID, file path, status, and target name")
    public String listTasks(
            @ShellOption(value = "--task", defaultValue = ShellOption.NULL,
                         help = "Filter by full or partial task ID") String taskPrefix,
            @ShellOption(value = "--status", defaultValue = ShellOption.NULL,
                          help = "Filter by status: PENDING, ENRICH_PENDING, ENRICHING, INDEXED, ENRICHED, FAILED, ENRICH_FAILED, AWAITING_HUMAN_REVIEW") String statusFilter,
            @ShellOption(value = "--target", defaultValue = ShellOption.NULL,
                         help = "Filter by target name (partial match)") String targetFilter,
            @ShellOption(value = "--verbose", defaultValue = "false",
                         help = "Show full task ID instead of truncating") boolean verbose,
            @ShellOption(value = "--limit", defaultValue = "50",
                         help = "Maximum number of rows to display") int limit) {

        TaskStatus status = null;
        if (statusFilter != null) {
            try {
                status = TaskStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "Error: Invalid status '" + statusFilter + "'. Valid values: PENDING, ENRICH_PENDING, ENRICHING, INDEXED, ENRICHED, FAILED, ENRICH_FAILED, AWAITING_HUMAN_REVIEW";
            }
        }

        List<Task> tasks;
        if (taskPrefix != null) {
            tasks = taskStore.findByPrefix(taskPrefix);
            if (statusFilter != null) {
                TaskStatus finalStatus = status;
                tasks = tasks.stream().filter(t -> t.status() == finalStatus).toList();
            }
            if (targetFilter != null) {
                tasks = tasks.stream().filter(t -> t.targetName() != null && t.targetName().contains(targetFilter)).toList();
            }
        } else if (status != null && targetFilter != null) {
            tasks = taskStore.findByStatusAndTargetPrefix(status, targetFilter);
        } else if (status != null) {
            tasks = taskStore.findByStatus(status);
        } else if (targetFilter != null) {
            tasks = taskStore.findByTargetPrefix(targetFilter);
        } else {
            tasks = taskStore.findAll();
        }

        if (tasks.isEmpty()) {
            return "No tasks found.";
        }

        int count = Math.min(tasks.size(), limit);
        int idWidth = verbose ? 66 : 16;
        int pathWidth = verbose ? 40 : 50;
        String headerFmt = "%-" + idWidth + "s %-" + pathWidth + "s %-14s %s%n";
        var sb = new StringBuilder();
        String idSep = "-".repeat(idWidth);
        String pathSep = "-".repeat(pathWidth);
        sb.append(String.format(headerFmt, "task_id", "file_path", "status", "target_name"));
        sb.append(String.format(headerFmt,
            idSep, pathSep, "--------------", "-----------"));

        for (int i = 0; i < count; i++) {
            Task t = tasks.get(i);
            String taskId = verbose ? truncate(t.taskId(), idWidth) : (t.taskId().length() > 12 ? t.taskId().substring(0, 12) + "..." : t.taskId());
            sb.append(String.format(headerFmt,
                taskId, truncateStart(t.filePath(), pathWidth - 2), t.status(), t.targetName()));
        }

        sb.append(String.format("%n%d row(s) (--limit %d)", tasks.size(), limit));
        if (tasks.size() > limit) {
            sb.append(", showing first ").append(limit);
        }
        return sb.toString();
    }

    @ShellMethod(key = "task findings", value = "List enrichment findings for a single task")
    public String listFindings(
            @ShellOption(value = "--task", help = "Full or partial task ID") String taskPrefix,
            @ShellOption(value = "--type", defaultValue = ShellOption.NULL,
                         help = "Filter by finding type (e.g. COMPONENT, ENDPOINT, CALL_GRAPH_EDGE)") String findingType,
            @ShellOption(value = "--limit", defaultValue = "20",
                         help = "Maximum number of findings to display") int limit) {

        Task task;
        try {
            task = taskStore.findByIdOrPrefix(taskPrefix);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        List<Map<String, Object>> findings;
        if (findingType != null) {
            findings = executionFindingStore.findByTaskIdAndType(task.taskId(), findingType.toUpperCase());
        } else {
            findings = executionFindingStore.findByTaskId(task.taskId());
        }

        String shortId = task.taskId().length() > 12 ? task.taskId().substring(0, 12) + "..." : task.taskId();
        var sb = new StringBuilder();
        sb.append(String.format("Task: %s -> %s (%s)%n%n", shortId, task.filePath(), task.targetName()));

        if (findings.isEmpty()) {
            sb.append("No findings for this task.");
            return sb.toString();
        }

        int count = Math.min(findings.size(), limit);
        sb.append(String.format("%-6s %-22s %-6s %s%n", "id", "finding_type", "resolved", "finding_json (truncated)"));
        sb.append(String.format("%-6s %-22s %-6s %s%n",
            "------", "----------------------", "------", "----------------------------------------"));

        for (int i = 0; i < count; i++) {
            Map<String, Object> row = findings.get(i);
            long id = ((Number) row.get("id")).longValue();
            String type = (String) row.get("finding_type");
            boolean resolved = ((Number) row.get("resolved")).intValue() == 1;
            String json = (String) row.get("finding_json");
            sb.append(String.format("%-6d %-22s %-6s %s%n",
                id, type, resolved ? "true" : "false", truncate(json, 60)));
        }

        sb.append(String.format("%n%d finding(s) (--limit %d)", findings.size(), limit));
        if (findings.size() > limit) {
            sb.append(", showing first ").append(limit);
        }
        return sb.toString();
    }

    @ShellMethod(key = "task set-status", value = "Change task status by --task or in batch by current status (--from)")
    public String setStatus(
            @ShellOption(value = "--task", defaultValue = ShellOption.NULL,
                         help = "Full or partial task ID") String taskPrefix,
            @ShellOption(value = "--from", defaultValue = ShellOption.NULL,
                          help = "Batch mode: current status to match (e.g. FAILED, ENRICH_FAILED)") String fromStatus,
            @ShellOption(value = "--status", help = "New status: PENDING, ENRICH_PENDING, INDEXED, ENRICHED, FAILED, ENRICH_FAILED, AWAITING_HUMAN_REVIEW") String newStatus,
            @ShellOption(value = "--delete-findings", defaultValue = "true",
                         help = "Also delete execution findings, topic links, and floating links") boolean deleteFindings,
            @ShellOption(value = "--dry-run", defaultValue = "false",
                         help = "Show what would be done without modifying anything") boolean dryRun) {

        TaskStatus status;
        try {
            status = TaskStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Error: Invalid status '" + newStatus + "'. Valid values: PENDING, ENRICH_PENDING, ENRICHING, INDEXED, ENRICHED, FAILED, ENRICH_FAILED, AWAITING_HUMAN_REVIEW";
        }

        if (taskPrefix != null && fromStatus != null) {
            return "Error: Provide either --task or --from, not both.";
        }

        if (fromStatus != null) {
            return setStatusBatch(fromStatus, status, deleteFindings, dryRun);
        }

        if (taskPrefix == null) {
            return "Error: Provide either --task or --from.";
        }

        List<Task> tasks;
        Optional<Task> exact = taskStore.findById(taskPrefix);
        if (exact.isPresent()) {
            tasks = List.of(exact.get());
        } else {
            tasks = taskStore.findByPrefix(taskPrefix);
            if (tasks.isEmpty()) {
                return "No task found matching: " + taskPrefix;
            }
        }

        var sb = new StringBuilder();
        int totalUpdated = 0;
        int totalDeletedFindings = 0;
        int totalDeletedTopicLinks = 0;
        int totalDeletedFloatingLinks = 0;

        for (Task task : tasks) {
            String shortId = task.taskId().length() > 12 ? task.taskId().substring(0, 12) + "..." : task.taskId();
            sb.append(String.format("Task: %s -> %s (%s)%n", shortId, task.filePath(), task.targetName()));
            sb.append(String.format("  Current status: %s -> %s%n", task.status(), status));

            int findingsCount = executionFindingStore.countByTaskId(task.taskId());
            int topicLinksCount = topicLinkStore.countByTaskId(task.taskId());
            int floatingLinksCount = floatingLinkStore.countByTaskId(task.taskId());

            if (deleteFindings) {
                sb.append(String.format("  Will delete: %d execution findings, %d topic links, %d floating links%n",
                    findingsCount, topicLinksCount, floatingLinksCount));
            }

            if (dryRun) {
                continue;
            }

            if (deleteFindings) {
                executionFindingStore.deleteByTaskId(task.taskId());
                topicLinkStore.deleteByTaskId(task.taskId());
                floatingLinkStore.deleteByTaskId(task.taskId());
                totalDeletedFindings += findingsCount;
                totalDeletedTopicLinks += topicLinksCount;
                totalDeletedFloatingLinks += floatingLinksCount;
                log.info("Deleted {} execution findings, {} topic links, {} floating links for task {}",
                    findingsCount, topicLinksCount, floatingLinksCount, task.taskId());
            }

            taskStore.updateStatus(task.taskId(), status);
            log.info("Updated task {} from {} to {}", task.taskId(), task.status(), status);
            totalUpdated++;
        }

        if (dryRun) {
            sb.append("  --dry-run: no changes made.");
            return sb.toString();
        }

        sb.append(String.format("  Updated %d task(s) to %s.", totalUpdated, status));
        if (deleteFindings) {
            sb.append(String.format(" Deleted %d findings, %d topic links, %d floating links across %d task(s).",
                totalDeletedFindings, totalDeletedTopicLinks, totalDeletedFloatingLinks, totalUpdated));
        }
        return sb.toString();
    }

    private String setStatusBatch(String fromStatusStr, TaskStatus newStatus, boolean deleteFindings, boolean dryRun) {
        TaskStatus fromStatus;
        try {
            fromStatus = TaskStatus.valueOf(fromStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Error: Invalid status '" + fromStatusStr + "'. Valid values: PENDING, ENRICH_PENDING, ENRICHING, INDEXED, ENRICHED, FAILED, ENRICH_FAILED, AWAITING_HUMAN_REVIEW";
        }

        List<Task> tasks = taskStore.findByStatus(fromStatus);
        if (tasks.isEmpty()) {
            return "No tasks found with status " + fromStatus + ".";
        }

        int totalFindings = 0;
        int totalTopicLinks = 0;
        int totalFloatingLinks = 0;

        int maxPreview = 3;
        var sb = new StringBuilder();
        sb.append(String.format("Batch update: %d task(s) from %s to %s%n", tasks.size(), fromStatus, newStatus));

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            int fc = executionFindingStore.countByTaskId(task.taskId());
            int tc = topicLinkStore.countByTaskId(task.taskId());
            int flc = floatingLinkStore.countByTaskId(task.taskId());
            totalFindings += fc;
            totalTopicLinks += tc;
            totalFloatingLinks += flc;

            if (i < maxPreview) {
                sb.append(String.format("  %s (%s)%n", truncateStart(task.filePath(), 50), task.targetName()));
                if (deleteFindings) {
                    sb.append(String.format("    findings=%d topicLinks=%d floatingLinks=%d%n", fc, tc, flc));
                }
            }
        }

        int remaining = tasks.size() - maxPreview;
        if (remaining > 0) {
            sb.append(String.format("  ... and %d more task(s)%n", remaining));
        }

        if (dryRun) {
            sb.append("  --dry-run: no changes made.");
            return sb.toString();
        }

        if (deleteFindings) {
            for (Task task : tasks) {
                executionFindingStore.deleteByTaskId(task.taskId());
                topicLinkStore.deleteByTaskId(task.taskId());
                floatingLinkStore.deleteByTaskId(task.taskId());
            }
        }

        int updated = taskStore.updateStatusByOldStatus(fromStatus, newStatus);
        sb.append(String.format("  Updated %d task(s) from %s to %s.", updated, fromStatus, newStatus));
        if (deleteFindings) {
            sb.append(String.format(" Deleted %d findings, %d topic links, %d floating links.",
                totalFindings, totalTopicLinks, totalFloatingLinks));
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private static String truncateStart(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : "..." + s.substring(s.length() - maxLen + 3);
    }
}
