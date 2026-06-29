package com.github.ehdez73.code2req.enrichment.application.port.input;

import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.enrichment.domain.model.CompletionStatus;

public interface EnrichProjectUseCase {
    CompletionStatus execute(ProjectManifest manifest, boolean dryRun, boolean resume, int llmThreshold);
}
