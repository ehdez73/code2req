package com.github.ehdez73.code2req.planner;

import com.github.ehdez73.code2req.model.PlannerDecision;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class Phase2Planner {

    private static final Logger log = LoggerFactory.getLogger(Phase2Planner.class);

    private final TaskStore taskStore;
    private final JdbcTemplate jdbc;
    private final List<QualificationRule> rules;

    public Phase2Planner(TaskStore taskStore, JdbcTemplate jdbc,
                         List<QualificationRule> rules) {
        this.taskStore = taskStore;
        this.jdbc = jdbc;
        this.rules = rules;
    }

    public List<PlannerDecision> plan() {
        List<Task> tasks = taskStore.findByStatus(TaskStatus.SUCCESS);
        if (tasks.isEmpty()) {
            log.info("No SUCCESS tasks found — planner has nothing to evaluate");
            return List.of();
        }

        var ctx = new PlanningContext(jdbc);

        List<PlannerDecision> decisions = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            decisions.add(evaluateTask(task, ctx));
        }
        return decisions;
    }

    private PlannerDecision evaluateTask(Task task, PlanningContext ctx) {
        List<QualificationReason> reasons = new ArrayList<>();
        for (QualificationRule rule : rules) {
            if (rule.evaluate(task, ctx)) {
                reasons.add(rule.reason());
            }
        }
        if (reasons.isEmpty()) {
            return PlannerDecision.notQualified(task.taskId(), task.filePath());
        }
        return PlannerDecision.qualified(task.taskId(), task.filePath(), Collections.unmodifiableList(reasons));
    }
}
