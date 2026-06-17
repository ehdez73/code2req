package com.github.ehdez73.code2req.planner;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;

public interface QualificationRule {
    QualificationReason reason();
    boolean evaluate(Task task, PlanningContext ctx);
}
