package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record XmlJmsListenerInfo(
    String destination,
    String beanName,
    String method,
    String responseDestination,
    String filePath,
    int lineNumber
) implements AnalysisFinding {
    @Override
    public String className() { return beanName; }
}
