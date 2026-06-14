package com.github.ehdez73.code2req.analyzer;

import com.github.ehdez73.code2req.analyzer.component.ComponentInfo;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResultBuilder {

    private final List<AnalysisFinding> findings = new ArrayList<>();

    public void addFinding(AnalysisFinding finding) {
        findings.add(finding);
    }

    @SuppressWarnings("unchecked")
    public <T extends AnalysisFinding> List<T> findings(Class<T> type) {
        return findings.stream()
            .filter(type::isInstance)
            .map(f -> (T) f)
            .toList();
    }

    public boolean hasControllerComponent() {
        return findings(ComponentInfo.class).stream()
            .anyMatch(c -> "Controller".equals(c.annotationType())
                || "RestController".equals(c.annotationType()));
    }

    public AnalysisResult build(String filePath) {
        return new AnalysisResult(filePath, List.copyOf(findings));
    }
}
