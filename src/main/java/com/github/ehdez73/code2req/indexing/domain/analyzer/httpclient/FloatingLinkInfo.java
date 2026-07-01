package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient;

public record FloatingLinkInfo(
    String method,
    String urlPattern,
    boolean isExpression,
    String clientType,
    String sourceFilePath,
    String sourceMethod,
    String targetEndpoint,
    double confidence,
    String resolvedStatus,
    int startLine,
    int endLine
) {
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_PENDING = "PENDING";

    public FloatingLinkInfo(String method, String urlPattern, boolean isExpression, String clientType,
                            String sourceFilePath, String sourceMethod, String targetEndpoint,
                            double confidence, String resolvedStatus) {
        this(method, urlPattern, isExpression, clientType, sourceFilePath, sourceMethod,
             targetEndpoint, confidence, resolvedStatus, 0, 0);
    }
}
