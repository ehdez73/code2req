package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.enrichment.domain.planner.PlanningContext;
import com.github.ehdez73.code2req.enrichment.domain.planner.QualificationRule;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ResolvedPhase1DepsRule implements QualificationRule {

    private static final Set<String> STRUCTURAL_TYPES = Set.of(
        FindingType.COMPONENT,
        FindingType.ENDPOINT,
        FindingType.SCHEDULED_TASK,
        FindingType.EVENT_LISTENER,
        FindingType.EVENT_PUBLISHER,
        FindingType.KAFKA_LISTENER,
        FindingType.KAFKA_PUBLISHER,
        FindingType.RABBITMQ_LISTENER,
        FindingType.RABBITMQ_PUBLISHER,
        FindingType.ACTIVEMQ_LISTENER,
        FindingType.ACTIVEMQ_PUBLISHER,
        FindingType.DB_ACCESS,
        FindingType.VALIDATOR,
        FindingType.BEAN_METHOD,
        FindingType.XML_BEAN,
        FindingType.XML_COMPONENT_SCAN,
        FindingType.XML_AOP_CONFIG,
        FindingType.XML_NAMESPACE_BEAN,
        FindingType.XML_SCHEDULED_TASK,
        FindingType.XML_JMS_LISTENER,
        FindingType.QUALIFIER,
        FindingType.SPRING_DATA_INTERFACE,
        FindingType.DATABASE_PROCEDURE_CALL,
        FindingType.CONSTRAINT_VALIDATOR,
        FindingType.NATIVE_SQL_QUERY,
        FindingType.JPQL_HQL_QUERY,
        FindingType.CALL_GRAPH_EDGE,
        FindingType.OUTBOUND_HTTP_CALL,
        FindingType.TEMPLATE_FORM,
        FindingType.TEMPLATE_LINK,
        FindingType.TEMPLATE_ENDPOINT_LINK
    );

    @Override
    public QualificationReason reason() {
        return QualificationReason.RESOLVED_PHASE1_DEPS;
    }

    @Override
    public boolean evaluate(Task task, PlanningContext ctx) {
        for (String type : STRUCTURAL_TYPES) {
            if (ctx.taskHasFindingType(task.taskId(), type)) {
                return true;
            }
        }
        return false;
    }
}
