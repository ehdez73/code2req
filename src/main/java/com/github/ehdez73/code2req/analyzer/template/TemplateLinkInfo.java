package com.github.ehdez73.code2req.analyzer.template;

public record TemplateLinkInfo(
    String templatePath,
    String httpMethod,
    String formAction,
    String matchedEndpointPath,
    String controllerName,
    double confidence,
    String linkType
) {}