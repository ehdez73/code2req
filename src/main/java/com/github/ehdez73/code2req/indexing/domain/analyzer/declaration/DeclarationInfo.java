package com.github.ehdez73.code2req.indexing.domain.analyzer.declaration;

import java.util.List;

public record DeclarationInfo(
    String className,
    String methodName,
    List<String> paramTypes,
    String filePath,
    int startLine,
    int endLine
) {
    public DeclarationInfo(String className, String methodName, List<String> paramTypes, String filePath) {
        this(className, methodName, paramTypes, filePath, 0, 0);
    }
}
