package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.enrichment.domain.planner.PlanningContext;
import com.github.ehdez73.code2req.enrichment.domain.planner.QualificationRule;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DtoValidationRule implements QualificationRule {

    private static final Set<String> FLOW_FINDING_TYPES = Set.of(
        FindingType.ENDPOINT,
        FindingType.COMPONENT,
        FindingType.DB_ACCESS,
        FindingType.SCHEDULED_TASK,
        FindingType.KAFKA_LISTENER,
        FindingType.RABBITMQ_LISTENER,
        FindingType.ACTIVEMQ_LISTENER,
        FindingType.EVENT_LISTENER
    );

    @Override
    public QualificationReason reason() {
        return QualificationReason.BEAN_VALIDATION;
    }

    @Override
    public boolean evaluate(Task task, PlanningContext ctx) {
        if (!ctx.taskHasFindingType(task.taskId(), FindingType.VALIDATOR)) {
            return false;
        }
        return FLOW_FINDING_TYPES.stream()
            .anyMatch(ft -> ctx.taskHasFindingType(task.taskId(), ft));
    }
}
