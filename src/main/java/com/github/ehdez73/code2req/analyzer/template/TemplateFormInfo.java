package com.github.ehdez73.code2req.analyzer.template;

import java.util.List;

public record TemplateFormInfo(
    String httpMethod,
    String urlPattern,
    boolean isExpression,
    List<String> fieldNames,
    String linkType,
    String templatePath
) {}