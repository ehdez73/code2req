package com.github.ehdez73.code2req.analyzer.web.endpoint;

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
public class EndpointVisitor implements AstAnalysisVisitor {

    private final List<EndpointDetector> detectors;

    public EndpointVisitor(List<EndpointDetector> detectors) {
        this.detectors = detectors;
    }

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<EndpointInfo> endpoints = new ArrayList<>();
        cu.accept(new DelegatingAdapter(endpoints, context.filePath(), detectors), null);
        endpoints.forEach(builder::addFinding);
    }

    private static class DelegatingAdapter extends VoidVisitorAdapter<Void> {

        private final List<EndpointInfo> findings;
        private final String filePath;
        private final List<EndpointDetector> detectors;
        private String className = "";

        DelegatingAdapter(List<EndpointInfo> findings, String filePath,
                          List<EndpointDetector> detectors) {
            this.findings = findings;
            this.filePath = filePath;
            this.detectors = detectors;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void v) {
            String prev = className;
            className = n.getNameAsString();

            for (EndpointDetector detector : detectors) {
                detector.detectClass(findings, n, className, filePath);
            }

            super.visit(n, v);
            className = prev;
        }

        @Override
        public void visit(MethodDeclaration n, Void v) {
            for (EndpointDetector detector : detectors) {
                detector.detect(findings, n, className, filePath);
            }
        }
    }
}
