package com.github.ehdez73.code2req.analyzer.xml;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record XmlNamespaceBeanInfo(
    String beanId,
    String namespaceUri,
    String elementName,
    String resolvedType,
    String filePath,
    int lineNumber
) implements AnalysisFinding {
    @Override
    public String className() { return resolvedType != null ? resolvedType : namespaceUri + ":" + elementName; }
}
