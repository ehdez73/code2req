package com.github.ehdez73.code2req.indexing.domain.analyzer.db;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

@Component
public class DbAccessVisitor implements AstAnalysisVisitor {

    private final List<DbAccessDetector> detectors;

    public DbAccessVisitor(List<DbAccessDetector> detectors) {
        this.detectors = detectors;
    }

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<DbAccessInfo> findings = new ArrayList<>();
        cu.accept(new DelegatingAdapter(findings, context.filePath(), detectors), null);
        findings.forEach(builder::addFinding);
    }

    private static class DelegatingAdapter extends VoidVisitorAdapter<Void> {

        private final List<DbAccessInfo> findings;
        private final String filePath;
        private final List<DbAccessDetector> detectors;
        private String className = "";

        DelegatingAdapter(List<DbAccessInfo> findings, String filePath,
                          List<DbAccessDetector> detectors) {
            this.findings = findings;
            this.filePath = filePath;
            this.detectors = detectors;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void v) {
            String prev = className;
            className = n.getNameAsString();

            for (DbAccessDetector detector : detectors) {
                detector.detectClass(findings, n, className, filePath);
            }

            super.visit(n, v);
            className = prev;
        }

        @Override
        public void visit(MethodDeclaration n, Void v) {
            for (DbAccessDetector detector : detectors) {
                detector.detect(findings, n, className, filePath);
            }
        }
    }
}
