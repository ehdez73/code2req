package com.github.ehdez73.code2req.indexing.domain.analyzer.aspect;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record AspectInfo(
    String className,
    String filePath,
    String methodName,
    String adviceType,
    String pointcutExpression,
    int startLine,
    int endLine
) implements AnalysisFinding {}
