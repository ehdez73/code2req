package com.github.ehdez73.code2req.analyzer.bean.xml;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record XmlBeanInfo(
    String beanId,
    String className,
    String scope,
    String factoryMethod,
    String filePath,
    int lineNumber
) implements AnalysisFinding {
    @Override
    public String className() { return className != null ? className : beanId; }
}
