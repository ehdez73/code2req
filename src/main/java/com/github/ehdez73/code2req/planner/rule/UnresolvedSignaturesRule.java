package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.ExecutionConfig;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.planner.PlanningContext;
import com.github.ehdez73.code2req.planner.QualificationRule;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the file has more unresolved call graph signatures
 * than the configured {@code llm-unresolved-threshold} (default: 5).
 * A high number of unresolved references indicates the file likely depends
 * on external types whose role requires LLM enrichment to explain.
 */
@Component
public class UnresolvedSignaturesRule implements QualificationRule {

    private final JdbcTemplate jdbc;
    private final int threshold;

    @Autowired
    public UnresolvedSignaturesRule(JdbcTemplate jdbc, ExecutionConfig executionConfig) {
        this(jdbc, executionConfig.resolvedUnresolvedThreshold());
    }

    public UnresolvedSignaturesRule(JdbcTemplate jdbc, int threshold) {
        this.jdbc = jdbc;
        this.threshold = threshold;
    }

    @Override
    public QualificationReason reason() {
        return QualificationReason.UNRESOLVED_SIGNATURES_EXCEEDED;
    }

    @Override
    public boolean evaluate(Task task, PlanningContext ctx) {
        int unresolvedCount = countUnresolved(task.taskId());
        return unresolvedCount > threshold;
    }

    private int countUnresolved(String taskId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM execution_findings WHERE task_id = ? AND finding_type = ? AND resolved = 0",
            Integer.class, taskId, FindingType.CALL_GRAPH_EDGE);
        return count != null ? count : 0;
    }
}
