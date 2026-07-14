package com.github.ehdez73.code2req.indexing.domain.analyzer.validator;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record ValidatorInfo(
    String className,
    String filePath,
    String annotationType,
    String elementName,
    String isValidBody,
    boolean isBuiltIn
) implements AnalysisFinding {}
