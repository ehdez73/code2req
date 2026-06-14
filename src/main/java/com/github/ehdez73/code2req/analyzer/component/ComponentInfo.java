package com.github.ehdez73.code2req.analyzer.component;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record ComponentInfo(
    String annotationType,
    String className,
    String packageName,
    String filePath
) implements AnalysisFinding {}
