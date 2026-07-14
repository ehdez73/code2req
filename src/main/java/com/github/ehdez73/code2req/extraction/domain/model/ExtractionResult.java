package com.github.ehdez73.code2req.extraction.domain.model;

import java.util.List;

public record ExtractionResult(
    int flowsExtracted,
    int ambiguityGaps,
    int awaitingReview,
    List<String> flowNames
) {
    public static ExtractionResult empty() {
        return new ExtractionResult(0, 0, 0, List.of());
    }
}
