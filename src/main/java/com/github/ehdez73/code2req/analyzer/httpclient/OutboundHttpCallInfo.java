package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record OutboundHttpCallInfo(
    String method,
    String urlPattern,
    boolean isExpression,
    String clientType,
    String encapsulatedIn,
    String className,
    String filePath
) implements AnalysisFinding {}
