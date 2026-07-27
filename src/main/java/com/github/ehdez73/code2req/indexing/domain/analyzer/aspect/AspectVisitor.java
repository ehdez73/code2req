package com.github.ehdez73.code2req.indexing.domain.analyzer.aspect;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class AspectVisitor implements AstAnalysisVisitor {

    private static final Logger log = LoggerFactory.getLogger(AspectVisitor.class);

    private static final Set<String> ADVICE_ANNOTATIONS = Set.of(
        "Around", "Before", "After", "AfterReturning", "AfterThrowing"
    );

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        AspectCollector collector = new AspectCollector();
        cu.accept(new AspectAstAdapter(context.filePath()), collector);
        if (!collector.findings.isEmpty()) {
            log.info("  AspectVisitor: found {} advice method(s) in {}", collector.findings.size(), context.filePath());
            collector.findings.forEach(builder::addFinding);
        }
    }

    static class AspectCollector {
        final List<AspectInfo> findings = new ArrayList<>();
    }

    static class AspectAstAdapter extends VoidVisitorAdapter<AspectCollector> {

        private final String filePath;

        AspectAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, AspectCollector collector) {
            boolean isAspect = n.getAnnotations().stream()
                .anyMatch(a -> "Aspect".equals(a.getNameAsString()));
            if (!isAspect) {
                super.visit(n, collector);
                return;
            }

            String className = n.getNameAsString();
            for (MethodDeclaration method : n.getMethods()) {
                Optional<AnnotationExpr> adviceAnn = method.getAnnotations().stream()
                    .filter(a -> ADVICE_ANNOTATIONS.contains(a.getNameAsString()))
                    .findFirst();
                if (adviceAnn.isEmpty()) continue;

                String adviceType = adviceAnn.get().getNameAsString();
                String pointcut = extractPointcut(adviceAnn.get());

                int startLine = method.getBegin().map(r -> r.line).orElse(0);
                int endLine = method.getEnd().map(r -> r.line).orElse(0);

                collector.findings.add(new AspectInfo(
                    className, filePath, method.getNameAsString(),
                    adviceType, pointcut, startLine, endLine
                ));
            }

            super.visit(n, collector);
        }

        private static String extractPointcut(AnnotationExpr annotation) {
            if (annotation.isSingleMemberAnnotationExpr()) {
                return annotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
            }
            if (annotation.isNormalAnnotationExpr()) {
                var pairs = annotation.asNormalAnnotationExpr().getPairs();
                for (var pair : pairs) {
                    String name = pair.getNameAsString();
                    if ("value".equals(name) || "pointcut".equals(name)) {
                        return pair.getValue().toString();
                    }
                }
            }
            return "";
        }
    }
}
