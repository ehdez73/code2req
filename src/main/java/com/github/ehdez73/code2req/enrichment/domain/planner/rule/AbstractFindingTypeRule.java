package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.enrichment.domain.planner.PlanningContext;
import com.github.ehdez73.code2req.enrichment.domain.planner.QualificationRule;

/**
 * Base class for qualification rules that check whether a task has a specific
 * {@link com.github.ehdez73.code2req.infrastructure.persistence.FindingType} persisted in its execution findings.
 */
public abstract class AbstractFindingTypeRule implements QualificationRule {

    private final QualificationReason reason;
    private final String findingType;

    protected AbstractFindingTypeRule(QualificationReason reason, String findingType) {
        this.reason = reason;
        this.findingType = findingType;
    }

    @Override
    public QualificationReason reason() {
        return reason;
    }

    @Override
    public boolean evaluate(Task task, PlanningContext ctx) {
        return ctx.taskHasFindingType(task.taskId(), findingType);
    }
}
