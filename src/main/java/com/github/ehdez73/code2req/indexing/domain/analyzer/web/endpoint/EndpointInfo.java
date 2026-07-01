package com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import java.util.List;

public record EndpointInfo(
    String httpMethod,
    String path,
    String controllerName,
    String methodName,
    List<String> pathVariables,
    List<String> queryParameters,
    String filePath,
    boolean servesView,
    String viewName,
    List<String> requestBodies,
    int startLine,
    int endLine
) implements AnalysisFinding {
    public EndpointInfo(String httpMethod, String path, String controllerName, String methodName,
                        List<String> pathVariables, List<String> queryParameters,
                        String filePath, boolean servesView, String viewName,
                        List<String> requestBodies) {
        this(httpMethod, path, controllerName, methodName, pathVariables, queryParameters,
             filePath, servesView, viewName, requestBodies, 0, 0);
    }

    @Override
    public String className() { return controllerName; }
}
