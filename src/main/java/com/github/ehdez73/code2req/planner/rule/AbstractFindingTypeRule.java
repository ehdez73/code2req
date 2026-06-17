package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.planner.PlanningContext;
import com.github.ehdez73.code2req.planner.QualificationRule;

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
