package com.github.ehdez73.code2req.extraction.application.port.input;

import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.extraction.domain.model.ExtractionResult;

public interface ExtractRequirementsUseCase {
    ExtractionResult execute(ProjectManifest manifest, boolean dryRun);
}
