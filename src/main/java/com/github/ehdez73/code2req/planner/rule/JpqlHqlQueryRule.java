package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

@Component
public class JpqlHqlQueryRule extends AbstractFindingTypeRule {
    public JpqlHqlQueryRule() {
        super(QualificationReason.JPQL_HQL_QUERY, FindingType.JPQL_HQL_QUERY);
    }
}
