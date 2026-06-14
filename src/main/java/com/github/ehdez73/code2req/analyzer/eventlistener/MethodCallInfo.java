package com.github.ehdez73.code2req.analyzer.eventlistener;

public record MethodCallInfo(
    String targetType,
    String methodName,
    int depth
) {}
