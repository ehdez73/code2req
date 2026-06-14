package com.github.ehdez73.code2req.analyzer.validator;

import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ValidatorVisitor implements AstAnalysisVisitor {

    private static final Set<String> BUILT_IN_ANNOTATIONS = Set.of(
        "NotNull", "NotEmpty", "NotBlank", "Size", "Min", "Max",
        "Email", "Pattern", "Positive", "PositiveOrZero", "Negative",
        "NegativeOrZero", "Past", "PastOrPresent", "Future", "FutureOrPresent",
        "AssertTrue", "AssertFalse", "DecimalMin", "DecimalMax", "Digits",
        "Valid", "Validated"
    );

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, String filePath) {
        ValidatorCollector collector = new ValidatorCollector();
        cu.accept(new ValidatorAstAdapter(filePath), collector);
        collector.findings.forEach(builder::addFinding);
    }

    static class ValidatorCollector {
        final List<ValidatorInfo> findings = new ArrayList<>();
    }

    static class ValidatorAstAdapter extends VoidVisitorAdapter<ValidatorCollector> {

        private final String filePath;
        private String className = "";

        ValidatorAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, ValidatorCollector collector) {
            String previousClassName = className;
            className = n.getNameAsString();

            for (MethodDeclaration method : n.getMethods()) {
                if ("isValid".equals(method.getNameAsString())) {
                    String body = method.getBody().map(Object::toString).orElse("");
                    collector.findings.add(new ValidatorInfo(
                        className,
                        filePath,
                        "Constraint",
                        method.getNameAsString(),
                        body,
                        false
                    ));
                }
            }

            super.visit(n, collector);
            className = previousClassName;
        }

        @Override
        public void visit(FieldDeclaration n, ValidatorCollector collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                String annName = ann.getNameAsString();
                if (BUILT_IN_ANNOTATIONS.contains(annName)) {
                    String fieldName = n.getVariables().isEmpty()
                        ? "" : n.getVariables().get(0).getNameAsString();
                    collector.findings.add(new ValidatorInfo(
                        className,
                        filePath,
                        annName,
                        fieldName,
                        "",
                        true
                    ));
                }
            }
        }
    }
}
