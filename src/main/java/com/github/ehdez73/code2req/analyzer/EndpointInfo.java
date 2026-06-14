package com.github.ehdez73.code2req.analyzer;

import java.util.List;

public record EndpointInfo(
    String httpMethod,
    String path,
    String controllerName,
    List<String> pathVariables,
    List<String> queryParameters,
    String filePath
) {}
