package com.github.ehdez73.code2req.indexing.domain.linker;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record ValidatorLinkInfo(
    String filePath,
    String className,
    String sourceElement,
    String annotationName,
    String targetFile,
    String targetClass,
    String isValidBody
) implements AnalysisFinding {}
