package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the file is a Spring Data repository interface
 * (extending {@code CrudRepository} or {@code JpaRepository}) that has no
 * AST body to analyse — its derived query methods require LLM interpretation.
 */
@Component
public class SpringDataInterfaceRule extends AbstractFindingTypeRule {
    public SpringDataInterfaceRule() {
        super(QualificationReason.SPRING_DATA_INTERFACE, FindingType.SPRING_DATA_INTERFACE);
    }
}
