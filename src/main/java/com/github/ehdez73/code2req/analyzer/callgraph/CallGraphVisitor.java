package com.github.ehdez73.code2req.analyzer.callgraph;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.analyzer.AstAnalysisVisitor;
import com.github.ehdez73.code2req.analyzer.declaration.DeclarationInfo;
import com.github.ehdez73.code2req.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class CallGraphVisitor implements AstAnalysisVisitor {

    private static final Set<String> JDK_PREFIXES = Set.of(
        "java.", "javax.", "jakarta.", "org.springframework.", "org.slf4j.",
        "com.fasterxml.jackson.", "org.apache.commons.", "lombok."
    );

    private static final Set<String> JDK_SHORT_TYPES = Set.of(
        "String", "Integer", "Long", "Double", "Float", "Boolean", "Short", "Byte", "Character",
        "List", "Map", "Set", "Queue", "Deque", "ArrayList", "HashMap", "HashSet", "LinkedList",
        "Optional", "Stream", "Collection", "Iterator", "Iterable", "Comparable", "Object",
        "Runnable", "Callable", "Supplier", "Consumer", "Function", "Predicate",
        "BigDecimal", "BigInteger", "LocalDate", "LocalTime", "LocalDateTime", "Instant",
        "Date", "Calendar", "UUID", "Path", "File", "Pattern", "Matcher",
        "StringBuilder", "StringBuffer", "Throwable", "Exception", "RuntimeException"
    );

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<CallGraphEdge> edges = new ArrayList<>();
        cu.accept(new CallGraphAstAdapter(context.filePath(), context.declarationRegistry()), edges);
        edges.forEach(builder::addFinding);
    }

    static class CallGraphAstAdapter extends VoidVisitorAdapter<List<CallGraphEdge>> {

        private final String filePath;
        private final GlobalDeclarationRegistry registry;
        private final Map<String, String> fieldTypes = new HashMap<>();
        private String currentClassName = "";
        private String currentMethodName = "";

        CallGraphAstAdapter(String filePath, GlobalDeclarationRegistry registry) {
            this.filePath = filePath;
            this.registry = registry;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, List<CallGraphEdge> collector) {
            currentClassName = n.getNameAsString();
            fieldTypes.clear();

            for (FieldDeclaration field : n.getFields()) {
                String rawType = field.getElementType().asString();
                int genericStart = rawType.indexOf('<');
                String fieldType = genericStart > 0 ? rawType.substring(0, genericStart).strip() : rawType.strip();
                for (var variable : field.getVariables()) {
                    fieldTypes.put(variable.getNameAsString(), fieldType);
                }
            }

            super.visit(n, collector);
        }

        @Override
        public void visit(MethodDeclaration n, List<CallGraphEdge> collector) {
            currentMethodName = n.getNameAsString();
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodCallExpr n, List<CallGraphEdge> collector) {
            if (currentClassName.isEmpty() || currentMethodName.isEmpty()) {
                super.visit(n, collector);
                return;
            }

            n.getScope().ifPresent(scope -> {
                String fieldName = resolveScopeName(scope);
                if (fieldName == null) return;

                String targetType = fieldTypes.get(fieldName);
                if (targetType == null) return;

                if (isJdkType(targetType)) return;

                String calledMethod = n.getNameAsString();
                int argCount = n.getArguments().size();

                resolveCall(targetType, calledMethod, argCount)
                    .ifPresent(collector::add);
            });

            super.visit(n, collector);
        }

        private static String resolveScopeName(com.github.javaparser.ast.expr.Expression scope) {
            if (scope instanceof NameExpr name) {
                return name.getNameAsString();
            }
            if (scope instanceof FieldAccessExpr fieldAccess) {
                return fieldAccess.getNameAsString();
            }
            return null;
        }

        private Optional<CallGraphEdge> resolveCall(String targetType, String methodName, int argCount) {
            var exactMatches = registry.findMethod(targetType, methodName, argCount);

            if (exactMatches.size() == 1) {
                DeclarationInfo match = exactMatches.getFirst();
                return Optional.of(CallGraphEdge.resolved(
                    currentClassName, currentMethodName, filePath,
                    match.className(), match.methodName(), match.filePath(), argCount));
            }

            if (exactMatches.size() > 1) {
                List<String> candidates = exactMatches.stream()
                    .map(d -> d.className() + "." + d.methodName() + "(" + String.join(",", d.paramTypes()) + ")")
                    .toList();
                return Optional.of(CallGraphEdge.ambiguous(
                    currentClassName, currentMethodName, filePath,
                    targetType, methodName, argCount, candidates));
            }

            var allOverloads = registry.findMethods(targetType, methodName);
            if (!allOverloads.isEmpty()) {
                return Optional.of(CallGraphEdge.unresolved(
                    currentClassName, currentMethodName, filePath,
                    targetType, methodName, argCount));
            }

            if (registry.hasClass(targetType)) {
                return Optional.of(CallGraphEdge.unresolved(
                    currentClassName, currentMethodName, filePath,
                    targetType, methodName, argCount));
            }

            return Optional.of(CallGraphEdge.unresolved(
                currentClassName, currentMethodName, filePath,
                targetType, methodName, argCount));
        }

        static boolean isJdkType(String typeName) {
            if (JDK_PREFIXES.stream().anyMatch(typeName::startsWith)) {
                return true;
            }
            return JDK_SHORT_TYPES.contains(typeName);
        }
    }
}
