package com.github.ehdez73.code2req.analyzer.component;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record BeanMethodInfo(
    String beanName,
    String returnType,
    String configurationClass,
    String filePath
) implements AnalysisFinding {
    @Override
    public String className() { return configurationClass + "." + beanName; }
}
