package com.github.ehdez73.code2req.extraction;

import java.util.List;

public class ExtractionResult {
    private final int flowsExtracted;
    private final int ambiguityGaps;
    private final int awaitingReview;
    private final List<String> flowNames;

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview) {
        this.flowsExtracted = flowsExtracted;
        this.ambiguityGaps = ambiguityGaps;
        this.awaitingReview = awaitingReview;
        this.flowNames = List.of();
    }

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview, List<String> flowNames) {
        this.flowsExtracted = flowsExtracted;
        this.ambiguityGaps = ambiguityGaps;
        this.awaitingReview = awaitingReview;
        this.flowNames = flowNames;
    }

    public static ExtractionResult empty() {
        return new ExtractionResult(0, 0, 0);
    }

    public int flowsExtracted() { return flowsExtracted; }
    public int ambiguityGaps() { return ambiguityGaps; }
    public int awaitingReview() { return awaitingReview; }
    public List<String> flowNames() { return flowNames; }
}
