package com.github.ehdez73.code2req.planner.rule;

import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.model.QualificationReason;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.planner.PlanningContext;
import com.github.ehdez73.code2req.planner.QualificationRule;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

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
