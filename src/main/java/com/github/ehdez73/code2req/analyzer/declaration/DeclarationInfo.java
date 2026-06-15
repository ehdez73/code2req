package com.github.ehdez73.code2req.analyzer.declaration;

import java.util.List;

public record DeclarationInfo(
    String className,
    String methodName,
    List<String> paramTypes,
    String filePath
) {}
