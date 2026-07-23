package com.github.ehdez73.code2req.indexing.domain.analyzer.bean;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class QualifierVisitor implements AstAnalysisVisitor {

    private static final Logger log = LoggerFactory.getLogger(QualifierVisitor.class);

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<QualifierInfo> qualifiers = new ArrayList<>();
        cu.accept(new QualifierAstAdapter(context.filePath()), qualifiers);
        if (!qualifiers.isEmpty()) {
            log.info("  QualifierVisitor: found {} @Qualifier annotation(s) in {}", qualifiers.size(), context.filePath());
            qualifiers.forEach(builder::addFinding);
        }
    }

    static class QualifierAstAdapter extends VoidVisitorAdapter<List<QualifierInfo>> {

        private final String filePath;
        private String currentClassName = "";

        QualifierAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, List<QualifierInfo> collector) {
            currentClassName = n.getNameAsString();
            super.visit(n, collector);
        }

        @Override
        public void visit(FieldDeclaration n, List<QualifierInfo> collector) {
            getQualifierValue(n.getAnnotationByName("Qualifier")).ifPresent(value -> {
                for (var variable : n.getVariables()) {
                    collector.add(new QualifierInfo(
                        currentClassName, variable.getNameAsString(), value, filePath));
                }
            });
            super.visit(n, collector);
        }

        @Override
        public void visit(ConstructorDeclaration n, List<QualifierInfo> collector) {
            for (Parameter param : n.getParameters()) {
                getQualifierValue(param.getAnnotationByName("Qualifier")).ifPresent(value -> {
                    collector.add(new QualifierInfo(
                        currentClassName, param.getNameAsString(), value, filePath));
                });
            }
            super.visit(n, collector);
        }

        private Optional<String> getQualifierValue(Optional<AnnotationExpr> annOpt) {
            if (annOpt.isEmpty()) return Optional.empty();
            AnnotationExpr ann = annOpt.get();
            if (ann.isSingleMemberAnnotationExpr()) {
                var value = ann.asSingleMemberAnnotationExpr().getMemberValue();
                if (value instanceof StringLiteralExpr sle) {
                    return Optional.of(sle.getValue());
                }
                return Optional.of(value.toString().replaceAll("[\"']", "").strip());
            }
            if (ann.isNormalAnnotationExpr()) {
                for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                    if ("value".equals(pair.getNameAsString())) {
                        var value = pair.getValue();
                        if (value instanceof StringLiteralExpr sle) {
                            return Optional.of(sle.getValue());
                        }
                        return Optional.of(value.toString().replaceAll("[\"']", "").strip());
                    }
                }
            }
            return Optional.empty();
        }
    }
}
