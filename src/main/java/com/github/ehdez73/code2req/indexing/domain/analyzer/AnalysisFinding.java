package com.github.ehdez73.code2req.indexing.domain.analyzer;

public interface AnalysisFinding {
    String className();
    String filePath();

    default boolean isResolved() {
        return true;
    }
}
