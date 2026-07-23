package com.github.ehdez73.code2req.indexing.domain.analyzer.bean;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record QualifierInfo(
    String className,
    String fieldName,
    String qualifierValue,
    String filePath
) implements AnalysisFinding {
    @Override
    public String className() { return className; }
}
