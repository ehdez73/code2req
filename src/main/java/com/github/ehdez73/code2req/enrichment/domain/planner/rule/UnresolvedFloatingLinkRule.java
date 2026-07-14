package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.enrichment.domain.planner.PlanningContext;
import com.github.ehdez73.code2req.enrichment.domain.planner.QualificationRule;
import com.github.ehdez73.code2req.infrastructure.persistence.FloatingLinkStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Qualifies a task when the file contains outbound HTTP calls whose
 * target endpoint could not be resolved deterministically
 * ({@code floating_links.resolved_status = 'PENDING'}). The LLM infers
 * the external service's business purpose from call-site context.
 */
@Component
public class UnresolvedFloatingLinkRule implements QualificationRule {

    private final FloatingLinkStore floatingLinkStore;
    private Set<String> unresolvedPaths;

    @Autowired
    public UnresolvedFloatingLinkRule(FloatingLinkStore floatingLinkStore) {
        this.floatingLinkStore = floatingLinkStore;
    }

    @Override
    public QualificationReason reason() {
        return QualificationReason.UNRESOLVED_FLOATING_LINK;
    }

    @Override
    public boolean evaluate(Task task, PlanningContext ctx) {
        if (unresolvedPaths == null) {
            unresolvedPaths = new HashSet<>(
                floatingLinkStore.findSourceFilePathsByResolvedStatus(FloatingLinkInfo.STATUS_PENDING));
        }
        return unresolvedPaths.contains(task.filePath());
    }
}
