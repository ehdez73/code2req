package com.github.ehdez73.code2req.enrichment.application.port.input;

import com.github.ehdez73.code2req.enrichment.domain.model.OrphanRecoveryResult;

public interface ResumeEnrichmentUseCase {
    OrphanRecoveryResult recover();
}
