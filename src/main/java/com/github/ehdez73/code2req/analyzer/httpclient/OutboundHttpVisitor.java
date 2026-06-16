package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OutboundHttpVisitor implements AstAnalysisVisitor {

    private final List<HttpClientDetector> detectors;

    public OutboundHttpVisitor(List<HttpClientDetector> detectors) {
        this.detectors = detectors;
    }

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<OutboundHttpCallInfo> findings = new ArrayList<>();
        cu.accept(new DelegatingAdapter(findings, context.filePath(), detectors), null);
        findings.forEach(builder::addFinding);
    }

    private static class DelegatingAdapter extends VoidVisitorAdapter<Void> {

        private final List<OutboundHttpCallInfo> findings;
        private final String filePath;
        private final List<HttpClientDetector> detectors;
        private String className = "";

        DelegatingAdapter(List<OutboundHttpCallInfo> findings, String filePath,
                          List<HttpClientDetector> detectors) {
            this.findings = findings;
            this.filePath = filePath;
            this.detectors = detectors;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void v) {
            String prev = className;
            className = n.getNameAsString();

            for (HttpClientDetector detector : detectors) {
                detector.detectClass(findings, n, className, filePath);
            }

            super.visit(n, v);
            className = prev;
        }

        @Override
        public void visit(MethodDeclaration n, Void v) {
            for (HttpClientDetector detector : detectors) {
                detector.detect(findings, n, className, filePath);
            }
        }
    }
}
