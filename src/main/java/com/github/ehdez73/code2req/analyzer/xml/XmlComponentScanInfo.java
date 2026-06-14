package com.github.ehdez73.code2req.analyzer.xml;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record XmlComponentScanInfo(
    String basePackage,
    String filePath
) implements AnalysisFinding {
    @Override
    public String className() { return "context:component-scan"; }
}
