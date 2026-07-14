package com.github.ehdez73.code2req.enrichment.domain.planner;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.common.domain.Task;

public interface QualificationRule {
    QualificationReason reason();
    boolean evaluate(Task task, PlanningContext ctx);
}
