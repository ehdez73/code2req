package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.store.FindingType;
import org.springframework.stereotype.Component;

/**
 * Qualifies a task when the file contains native SQL queries
 * ({@code @Query(nativeQuery=true)}, {@code @NamedNativeQuery},
 * {@code EntityManager.createNativeQuery()}, raw JDBC) whose
 * database-specific business logic requires LLM enrichment.
 */
@Component
public class NativeSqlQueryRule extends AbstractFindingTypeRule {
    public NativeSqlQueryRule() {
        super(QualificationReason.NATIVE_SQL_QUERY, FindingType.NATIVE_SQL_QUERY);
    }
}
