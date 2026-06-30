package com.github.ehdez73.code2req.extraction;

import java.nio.file.Path;
import java.util.List;

public class ExtractionResult {
    private final int flowsExtracted;
    private final int ambiguityGaps;
    private final int awaitingReview;
    private final List<String> flowNames;
    private final List<Path> generatedFiles;

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview) {
        this.flowsExtracted = flowsExtracted;
        this.ambiguityGaps = ambiguityGaps;
        this.awaitingReview = awaitingReview;
        this.flowNames = List.of();
        this.generatedFiles = List.of();
    }

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview, List<String> flowNames) {
        this.flowsExtracted = flowsExtracted;
        this.ambiguityGaps = ambiguityGaps;
        this.awaitingReview = awaitingReview;
        this.flowNames = flowNames;
        this.generatedFiles = List.of();
    }

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview, List<String> flowNames, List<Path> generatedFiles) {
        this.flowsExtracted = flowsExtracted;
        this.ambiguityGaps = ambiguityGaps;
        this.awaitingReview = awaitingReview;
        this.flowNames = flowNames;
        this.generatedFiles = generatedFiles != null ? generatedFiles : List.of();
    }

    public static ExtractionResult empty() {
        return new ExtractionResult(0, 0, 0);
    }

    public int flowsExtracted() { return flowsExtracted; }
    public int ambiguityGaps() { return ambiguityGaps; }
    public int awaitingReview() { return awaitingReview; }
    public List<String> flowNames() { return flowNames; }
    public List<Path> generatedFiles() { return generatedFiles; }
}
