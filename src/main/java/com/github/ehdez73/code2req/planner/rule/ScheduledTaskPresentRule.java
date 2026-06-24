package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the file contains a {@code @Scheduled} annotation.
 * The cron/fixed-delay/fixed-rate expression conveys <em>when</em> but not
 * <em>what</em> business operation the method performs, warranting LLM enrichment.
 */
@Component
public class ScheduledTaskPresentRule extends AbstractFindingTypeRule {
    public ScheduledTaskPresentRule() {
        super(QualificationReason.SCHEDULED_TASK_PRESENT, FindingType.SCHEDULED_TASK);
    }
}
