package com.github.ehdez73.code2req.analyzer;

import java.util.List;

public record AnalysisResult(
    String filePath,
    List<AnalysisFinding> findings
) {
    @SuppressWarnings("unchecked")
    public <T extends AnalysisFinding> List<T> findings(Class<T> type) {
        return findings.stream()
            .filter(type::isInstance)
            .map(f -> (T) f)
            .toList();
    }
}
