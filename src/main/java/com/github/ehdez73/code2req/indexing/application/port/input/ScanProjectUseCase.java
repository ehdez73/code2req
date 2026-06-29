package com.github.ehdez73.code2req.indexing.application.port.input;

import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.common.domain.ScanTarget;
import com.github.ehdez73.code2req.indexing.domain.model.ScanPipelineResult;

public interface ScanProjectUseCase {
    ScanPipelineResult execute(ProjectManifest manifest, ScanTarget target, boolean resume);
}
