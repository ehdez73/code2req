package com.github.ehdez73.code2req.indexing.domain.analyzer.db;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

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
