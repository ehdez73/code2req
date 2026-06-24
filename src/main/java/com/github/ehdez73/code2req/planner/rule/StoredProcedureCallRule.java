package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
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
