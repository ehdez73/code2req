package com.github.ehdez73.code2req.indexing.domain.analyzer.bean;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ComponentVisitor implements AstAnalysisVisitor {

    private static final Logger log = LoggerFactory.getLogger(ComponentVisitor.class);

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<ComponentInfo> components = new ArrayList<>();
        cu.accept(new ComponentAstAdapter(context.filePath()), components);
        if (!components.isEmpty()) {
            log.info("  ComponentVisitor: found {} component(s) in {}", components.size(), context.filePath());
            components.forEach(builder::addFinding);
        }
    }

    static class ComponentAstAdapter extends VoidVisitorAdapter<List<ComponentInfo>> {

        private static final Set<String> STEREOTYPES = Set.of(
            "Component", "Service", "Repository", "Controller", "RestController"
        );

        private final String filePath;

        ComponentAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, List<ComponentInfo> collector) {
            if (n.isInterface()) {
                super.visit(n, collector);
                return;
            }

            String annotationType = "other";
            for (AnnotationExpr ann : n.getAnnotations()) {
                String name = ann.getNameAsString();
                if (STEREOTYPES.contains(name)) {
                    annotationType = name;
                    break;
                }
            }

            String packageName = n.getFullyQualifiedName()
                .map(fqn -> {
                    int lastDot = fqn.lastIndexOf('.');
                    return lastDot > 0 ? fqn.substring(0, lastDot) : "";
                })
                .orElse("");

            collector.add(new ComponentInfo(annotationType, n.getNameAsString(), packageName, filePath));
            super.visit(n, collector);
        }
    }
}
