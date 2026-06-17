package com.github.ehdez73.code2req.analyzer.bean.xml;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record XmlAopConfigInfo(
    String kind,
    String filePath,
    int lineNumber
) implements AnalysisFinding {
    @Override
    public String className() { return kind; }
}
