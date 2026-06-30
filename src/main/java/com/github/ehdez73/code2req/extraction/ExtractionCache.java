package com.github.ehdez73.code2req.extraction;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;

import java.util.List;

public record ExtractionCache(
    CrossReferencedResult crossRefResult,
    List<OrphanedMethod> orphanedMethods,
    List<AmbiguityGap> quarantineGaps
) {}
