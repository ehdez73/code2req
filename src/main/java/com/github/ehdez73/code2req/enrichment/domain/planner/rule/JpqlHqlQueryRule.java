package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the file contains JPQL or HQL queries
 * ({@code @Query}, {@code @NamedQuery}, {@code EntityManager.createQuery()},
 * {@code Session.createQuery()}) whose business logic cannot be inferred
 * from AST structure alone.
 */
@Component
public class JpqlHqlQueryRule extends AbstractFindingTypeRule {
    public JpqlHqlQueryRule() {
        super(QualificationReason.JPQL_HQL_QUERY, FindingType.JPQL_HQL_QUERY);
    }
}
