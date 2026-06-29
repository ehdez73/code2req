package com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import java.util.List;

public record EndpointInfo(
    String httpMethod,
    String path,
    String controllerName,
    List<String> pathVariables,
    List<String> queryParameters,
    String filePath,
    boolean servesView,
    String viewName
) implements AnalysisFinding {
    @Override
    public String className() { return controllerName; }
}
