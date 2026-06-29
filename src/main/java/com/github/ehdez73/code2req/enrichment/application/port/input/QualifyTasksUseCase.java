package com.github.ehdez73.code2req.enrichment.application.port.input;

import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.enrichment.domain.model.PlannerDecision;

import java.util.List;

public interface QualifyTasksUseCase {
    List<PlannerDecision> evaluate(ProjectManifest manifest);
}
