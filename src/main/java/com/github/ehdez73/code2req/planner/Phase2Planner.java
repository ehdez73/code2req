package com.github.ehdez73.code2req.planner;

import com.github.ehdez73.code2req.model.PlannerDecision;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FindingType;
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
    private final ExecutionFindingStore findingStore;

    public Phase2Planner(TaskStore taskStore, JdbcTemplate jdbc,
                         List<QualificationRule> rules,
                         ExecutionFindingStore findingStore) {
        this.taskStore = taskStore;
        this.jdbc = jdbc;
        this.rules = rules;
        this.findingStore = findingStore;
    }

    public List<PlannerDecision> plan() {
        List<Task> tasks = taskStore.findByStatusesWithoutFinding(
            List.of(TaskStatus.INDEXED, TaskStatus.ENRICH_PENDING),
            FindingType.SEMANTIC_ENRICHMENT);
        if (tasks.isEmpty()) {
            log.info("No INDEXED or ENRICH_PENDING tasks without enrichment findings — planner has nothing to evaluate");
            return List.of();
        }

        var ctx = new PlanningContext(jdbc);

        List<PlannerDecision> decisions = new ArrayList<>(tasks.size());
        int priorPending = 0;
        for (Task task : tasks) {
            if (task.status() == TaskStatus.ENRICH_PENDING) {
                decisions.add(PlannerDecision.qualified(
                    task.taskId(), task.filePath(), task.targetName(), List.of()));
                priorPending++;
                continue;
            }

            PlannerDecision decision = evaluateTask(task, ctx);
            if (decision.qualified()) {
                taskStore.updateStatus(task.taskId(), TaskStatus.ENRICH_PENDING);
                log.debug("Transitioned task {} ({}) from INDEXED to ENRICH_PENDING",
                    task.taskId(), task.filePath());
            }
            decisions.add(decision);
        }

        int qualified = (int) decisions.stream().filter(PlannerDecision::qualified).count();
        log.info("Planner evaluated {} task(s): {} qualified ({} from prior ENRICH_PENDING), {} not qualified",
            decisions.size(), qualified, priorPending, decisions.size() - qualified);
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
            return PlannerDecision.notQualified(task.taskId(), task.filePath(), task.targetName());
        }
        return PlannerDecision.qualified(task.taskId(), task.filePath(), task.targetName(), Collections.unmodifiableList(reasons));
    }
}
