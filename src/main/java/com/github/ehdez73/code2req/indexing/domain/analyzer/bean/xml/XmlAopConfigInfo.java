package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record XmlAopConfigInfo(
    String kind,
    String filePath,
    int lineNumber
) implements AnalysisFinding {
    @Override
    public String className() { return kind; }
}
