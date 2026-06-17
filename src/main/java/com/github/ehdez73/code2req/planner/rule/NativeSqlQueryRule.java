package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

@Component
public class NativeSqlQueryRule extends AbstractFindingTypeRule {
    public NativeSqlQueryRule() {
        super(QualificationReason.NATIVE_SQL_QUERY, FindingType.NATIVE_SQL_QUERY);
    }
}
