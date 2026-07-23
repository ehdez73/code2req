package com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AstAnalysisVisitor;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.DeclarationInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.ehdez73.code2req.common.util.LibraryTypeResolver;
import com.github.ehdez73.code2req.indexing.domain.model.AllowedLibrariesConfig;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class CallGraphVisitor implements AstAnalysisVisitor {

    private static final Logger log = LoggerFactory.getLogger(CallGraphVisitor.class);

    private final List<String> allPrefixes;

    public CallGraphVisitor(AllowedLibrariesConfig config) {
        this.allPrefixes = Stream.concat(
            config.resolvedJdkPrefixes().stream(),
            config.resolvedFrameworkPrefixes().stream()
        ).collect(Collectors.toList());
    }

    private static final Set<String> JDK_SHORT_TYPES = Set.of(
        "String", "Integer", "Long", "Double", "Float", "Boolean", "Short", "Byte", "Character",
        "List", "Map", "Set", "Queue", "Deque", "ArrayList", "HashMap", "HashSet", "LinkedList",
        "Optional", "Stream", "Collection", "Iterator", "Iterable", "Comparable", "Object",
        "Runnable", "Callable", "Supplier", "Consumer", "Function", "Predicate",
        "BigDecimal", "BigInteger", "LocalDate", "LocalTime", "LocalDateTime", "Instant",
        "Date", "Calendar", "UUID", "Path", "File", "Pattern", "Matcher",
        "StringBuilder", "StringBuffer", "Throwable", "Exception", "RuntimeException"
    );
    public static final String ORG_SPRINGFRAMEWORK_DATA = "org.springframework.data.";

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context) {
        List<CallGraphEdge> edges = new ArrayList<>();
        cu.accept(new CallGraphAstAdapter(context.filePath(), context.declarationRegistry(), allPrefixes, cu), edges);
        if (!edges.isEmpty()) {
            log.info("  CallGraphVisitor: found {} call graph edge(s) in {}", edges.size(), context.filePath());
            edges.stream().distinct().forEach(builder::addFinding);
        }
    }

    static class CallGraphAstAdapter extends VoidVisitorAdapter<List<CallGraphEdge>> {

        private final String filePath;
        private final GlobalDeclarationRegistry registry;
        private final List<String> allPrefixes;
        private final CompilationUnit cu;
        private final Map<String, String> fieldTypes = new HashMap<>();
        private final Map<String, String> parameterTypes = new HashMap<>();
        private String currentClassName = "";
        private String currentMethodName = "";

        CallGraphAstAdapter(String filePath, GlobalDeclarationRegistry registry, List<String> allPrefixes, CompilationUnit cu) {
            this.filePath = filePath;
            this.registry = registry;
            this.allPrefixes = allPrefixes;
            this.cu = cu;
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
            parameterTypes.clear();
            for (var param : n.getParameters()) {
                String rawType = param.getType().asString();
                int genericStart = rawType.indexOf('<');
                String paramType = genericStart > 0 ? rawType.substring(0, genericStart).strip() : rawType.strip();
                parameterTypes.put(param.getNameAsString(), paramType);
            }
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodCallExpr n, List<CallGraphEdge> collector) {
            if (currentClassName.isEmpty() || currentMethodName.isEmpty()) {
                super.visit(n, collector);
                return;
            }

            String calledMethod = n.getNameAsString();
            int argCount = n.getArguments().size();

            var scope = n.getScope().orElse(null);
            String targetType = resolveTargetType(scope);

            if (targetType != null) {
                if (!isLibraryType(targetType)
                    && !LibraryTypeResolver.isAllowedLibrary(targetType, cu, allPrefixes)) {
                    resolveCall(targetType, calledMethod, argCount)
                        .ifPresent(collector::add);
                }
            } else if (scope == null) {
                // No scope → same-class call (e.g., method() without this.)
                resolveCall(currentClassName, calledMethod, argCount)
                    .ifPresent(collector::add);
            }

            super.visit(n, collector);
        }

        private String resolveTargetType(com.github.javaparser.ast.expr.Expression scope) {
            if (scope == null) return null;

            String name = resolveScopeName(scope);
            if (name == null) return null;

            // "this" scope → current class
            if ("this".equals(name)) {
                return currentClassName;
            }

            // Field-scoped call
            String fieldType = fieldTypes.get(name);
            if (fieldType != null) return fieldType;

            // Parameter-scoped call
            String paramType = parameterTypes.get(name);
            if (paramType != null) return paramType;

            return null;
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

                List<DeclarationInfo> implementations = registry.findImplementations(targetType, methodName, argCount);
                if (implementations.size() > 1) {
                    List<String> candidates = implementations.stream()
                        .map(d -> d.className() + "." + d.methodName() + "(" + String.join(",", d.paramTypes()) + ")")
                        .toList();
                    return Optional.of(CallGraphEdge.ambiguous(
                        currentClassName, currentMethodName, filePath,
                        targetType, methodName, argCount, candidates));
                }

                if (implementations.size() == 1) {
                    DeclarationInfo impl = implementations.getFirst();
                    return Optional.of(CallGraphEdge.resolved(
                        currentClassName, currentMethodName, filePath,
                        targetType, methodName, impl.filePath(), argCount,
                        impl.startLine(), impl.endLine()));
                }

                return Optional.of(CallGraphEdge.resolved(
                    currentClassName, currentMethodName, filePath,
                    targetType, methodName, match.filePath(), argCount,
                    match.startLine(), match.endLine()));
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
                for (var parent : registry.getSuperTypes(targetType)) {
                    var result = resolveThroughInheritance(targetType, parent.simpleName(), methodName, argCount);
                    if (result.isPresent()) return result;
                }
                if (hasSpringDataAncestor(targetType, new HashSet<>())) {
                    String targetFile = registry.findByClassName(targetType).stream()
                        .findFirst().map(DeclarationInfo::filePath).orElse("");
                    return Optional.of(CallGraphEdge.resolved(
                        currentClassName, currentMethodName, filePath,
                        targetType, methodName, targetFile, argCount, 0, 0));
                }
                return Optional.of(CallGraphEdge.unresolved(
                    currentClassName, currentMethodName, filePath,
                    targetType, methodName, argCount));
            }

            return Optional.of(CallGraphEdge.unresolved(
                currentClassName, currentMethodName, filePath,
                targetType, methodName, argCount));
        }

        private Optional<CallGraphEdge> resolveThroughInheritance(
                String originalTarget, String currentType, String methodName, int argCount) {
            var matches = registry.findMethod(currentType, methodName, argCount);
            if (matches.size() == 1) {
                DeclarationInfo match = matches.getFirst();
                return Optional.of(CallGraphEdge.resolved(
                    currentClassName, currentMethodName, filePath,
                    originalTarget, methodName, match.filePath(), argCount,
                    match.startLine(), match.endLine()));
            }
            for (var parent : registry.getSuperTypes(currentType)) {
                var result = resolveThroughInheritance(originalTarget, parent.simpleName(), methodName, argCount);
                if (result.isPresent()) return result;
            }
            return Optional.empty();
        }

        private boolean hasSpringDataAncestor(String className, Set<String> visited) {
            if (!visited.add(className)) return false;
            for (var parent : registry.getSuperTypes(className)) {
                if (parent.fqn() != null && parent.fqn().startsWith(ORG_SPRINGFRAMEWORK_DATA))
                    return true;
                if (hasSpringDataAncestor(parent.simpleName(), visited))
                    return true;
            }
            return false;
        }

        private boolean isLibraryType(String typeName) {
            if (allPrefixes.stream().anyMatch(typeName::startsWith)) {
                return true;
            }
            return JDK_SHORT_TYPES.contains(typeName);
        }
    }
}
