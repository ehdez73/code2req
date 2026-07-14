package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record XmlComponentScanInfo(
    String basePackage,
    String filePath
) implements AnalysisFinding {
    @Override
    public String className() { return "context:component-scan"; }
}
