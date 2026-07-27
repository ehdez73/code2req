package com.github.ehdez73.code2req.indexing.domain.linker;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record AopAdviceLinkInfo(
    String filePath,
    String className,
    String sourceMethod,
    String annotationName,
    String targetFile,
    String targetAspectClass,
    String targetAdviceMethod,
    String adviceType,
    String pointcutExpression
) implements AnalysisFinding {}
