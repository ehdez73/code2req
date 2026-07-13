package com.github.ehdez73.code2req.indexing.domain.analyzer.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    boolean isTransactionRoot,
    int startLine,
    int endLine,
    int paramCount
) implements AnalysisFinding {

    @Override
    @JsonIgnore
    public boolean isResolved() {
        return true;
    }

    public DbAccessInfo(String type, String sql, String tableHint, String procedureName,
                        String methodName, String className, String filePath,
                        String entityType, boolean isTransactionRoot) {
        this(type, sql, tableHint, procedureName, methodName, className, filePath,
             entityType, isTransactionRoot, 0, 0, 0);
    }
}
