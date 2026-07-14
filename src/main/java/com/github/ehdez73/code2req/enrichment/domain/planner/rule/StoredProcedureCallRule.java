package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the file contains a stored procedure call
 * ({@code @Procedure} annotation, {@code CALL}/{@code EXECUTE} block)
 * whose business logic requires LLM interpretation.
 */
@Component
public class StoredProcedureCallRule extends AbstractFindingTypeRule {
    public StoredProcedureCallRule() {
        super(QualificationReason.STORED_PROCEDURE_CALL, FindingType.DATABASE_PROCEDURE_CALL);
    }
}
