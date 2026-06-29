package com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener;

public record MethodCallInfo(
    String targetType,
    String methodName,
    int depth
) {}
