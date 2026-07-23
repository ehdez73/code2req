package com.github.ehdez73.code2req.indexing.domain.analyzer.bean;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record ComponentInfo(
    String annotationType,
    String className,
    String packageName,
    String filePath,
    boolean primary
) implements AnalysisFinding {}
