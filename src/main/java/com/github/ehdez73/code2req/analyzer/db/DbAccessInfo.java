package com.github.ehdez73.code2req.analyzer.db;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record DbAccessInfo(
    String type,
    String sql,
    String tableHint,
    String procedureName,
    String methodName,
    String className,
    String filePath,
    String entityType,
    boolean isTransactionRoot
) implements AnalysisFinding {}
