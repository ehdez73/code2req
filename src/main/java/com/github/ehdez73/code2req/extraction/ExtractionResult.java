package com.github.ehdez73.code2req.extraction;

import java.nio.file.Path;
import java.util.List;

public class ExtractionResult {
    private final int flowsExtracted;
    private final int ambiguityGaps;
    private final int awaitingReview;
    private final List<String> flowNames;
    private final List<Path> generatedFiles;
    private final String blockedReason;

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview) {
        this(flowsExtracted, ambiguityGaps, awaitingReview, List.of(), List.of(), null);
    }

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview, List<String> flowNames) {
        this(flowsExtracted, ambiguityGaps, awaitingReview, flowNames, List.of(), null);
    }

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview, List<String> flowNames, List<Path> generatedFiles) {
        this(flowsExtracted, ambiguityGaps, awaitingReview, flowNames, generatedFiles, null);
    }

    public ExtractionResult(int flowsExtracted, int ambiguityGaps, int awaitingReview, List<String> flowNames, List<Path> generatedFiles, String blockedReason) {
        this.flowsExtracted = flowsExtracted;
        this.ambiguityGaps = ambiguityGaps;
        this.awaitingReview = awaitingReview;
        this.flowNames = flowNames;
        this.generatedFiles = generatedFiles != null ? generatedFiles : List.of();
        this.blockedReason = blockedReason;
    }

    public static ExtractionResult empty() {
        return new ExtractionResult(0, 0, 0);
    }

    public static ExtractionResult blocked(String reason) {
        return new ExtractionResult(0, 0, 0, List.of(), List.of(), reason);
    }

    public int flowsExtracted() { return flowsExtracted; }
    public int ambiguityGaps() { return ambiguityGaps; }
    public int awaitingReview() { return awaitingReview; }
    public List<String> flowNames() { return flowNames; }
    public List<Path> generatedFiles() { return generatedFiles; }
    public boolean isBlocked() { return blockedReason != null; }
    public String blockedReason() { return blockedReason; }
}
