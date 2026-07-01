package com.github.ehdez73.code2req.indexing.domain.analyzer.declaration;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class Pass1DeclarationCollector {

    private static final Logger log = LoggerFactory.getLogger(Pass1DeclarationCollector.class);

    public void collect(CompilationUnit cu, GlobalDeclarationRegistry registry, String filePath) {
        try {
            cu.accept(new DeclarationAstAdapter(filePath, registry), null);
        } catch (Exception e) {
            log.warn("Failed to collect declarations from {}: {}", filePath, e.getMessage());
        }
    }

    static class DeclarationAstAdapter extends VoidVisitorAdapter<Void> {

        private final String filePath;
        private final GlobalDeclarationRegistry registry;

        DeclarationAstAdapter(String filePath, GlobalDeclarationRegistry registry) {
            this.filePath = filePath;
            this.registry = registry;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void v) {
            String className = n.getNameAsString();

            registry.register(new DeclarationInfo(className, "<clinit>", List.of(), filePath));

            for (MethodDeclaration method : n.getMethods()) {
                List<String> paramTypes = new ArrayList<>();
                for (var param : method.getParameters()) {
                    paramTypes.add(param.getTypeAsString());
                }
                int startLine = method.getBegin().map(r -> r.line).orElse(0);
                int endLine = method.getEnd().map(r -> r.line).orElse(0);
                registry.register(new DeclarationInfo(className, method.getNameAsString(), paramTypes, filePath, startLine, endLine));
            }

            super.visit(n, v);
        }
    }
}
