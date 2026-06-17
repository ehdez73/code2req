package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTaskPresentRule extends AbstractFindingTypeRule {
    public ScheduledTaskPresentRule() {
        super(QualificationReason.SCHEDULED_TASK_PRESENT, FindingType.SCHEDULED_TASK);
    }
}
