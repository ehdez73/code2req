package com.github.ehdez73.code2req.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EndpointVisitor implements AstAnalysisVisitor {

    @Override
    public void analyze(CompilationUnit cu, AnalysisResultBuilder builder, String filePath) {
        if (!builder.hasControllerComponent()) {
            return;
        }
        List<EndpointInfo> endpoints = new ArrayList<>();
            cu.accept(new EndpointAstAdapter(filePath), endpoints);
        endpoints.forEach(builder::addEndpoint);
    }

    static class EndpointAstAdapter extends VoidVisitorAdapter<List<EndpointInfo>> {

        private static final Map<String, String> METHOD_MAPPINGS = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping", "PATCH"
        );

        private final String filePath;
        private String classLevelPath = "";
        private String controllerName = "";

        EndpointAstAdapter(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, List<EndpointInfo> collector) {
            controllerName = n.getNameAsString();
            for (AnnotationExpr ann : n.getAnnotations()) {
                if ("RequestMapping".equals(ann.getNameAsString())) {
                    classLevelPath = extractStringAttr(ann, "value")
                        .or(() -> extractStringAttr(ann, "path"))
                        .orElse("");
                }
            }
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodDeclaration n, List<EndpointInfo> collector) {
            for (AnnotationExpr ann : n.getAnnotations()) {
                String name = ann.getNameAsString();
                String httpMethod = METHOD_MAPPINGS.get(name);
                if (httpMethod == null && "RequestMapping".equals(name)) {
                    httpMethod = extractMethodAttr(ann).orElse("");
                }
                if (httpMethod == null) {
                    continue;
                }

                String methodPath = extractStringAttr(ann, "value")
                    .or(() -> extractStringAttr(ann, "path"))
                    .orElse("");
                String fullPath = combinePaths(classLevelPath, methodPath);
                List<String> pathVars = extractPathVariables(n);
                List<String> queryParams = extractQueryParams(n);

                collector.add(new EndpointInfo(httpMethod, fullPath, controllerName, pathVars, queryParams, filePath));
            }
        }

        static String combinePaths(String classPath, String methodPath) {
            if (classPath.isEmpty() && methodPath.isEmpty()) {
                return "";
            }
            if (classPath.isEmpty()) {
                return methodPath.startsWith("/") ? methodPath : "/" + methodPath;
            }
            if (methodPath.isEmpty()) {
                return classPath.startsWith("/") ? classPath : "/" + classPath;
            }
            String cp = classPath.endsWith("/") ? classPath.substring(0, classPath.length() - 1) : classPath;
            if (!cp.startsWith("/")) cp = "/" + cp;
            String mp = methodPath.startsWith("/") ? methodPath.substring(1) : methodPath;
            return cp + "/" + mp;
        }

        static Optional<String> extractStringAttr(AnnotationExpr ann, String attr) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if (pair.getNameAsString().equals(attr)) {
                        return Optional.of(pair.getValue().toString().replaceAll("^\"|\"$", ""));
                    }
                }
            }
            if (ann instanceof SingleMemberAnnotationExpr smae && "value".equals(attr)) {
                return Optional.of(smae.getMemberValue().toString().replaceAll("^\"|\"$", ""));
            }
            return Optional.empty();
        }

        static Optional<String> extractMethodAttr(AnnotationExpr ann) {
            if (ann instanceof NormalAnnotationExpr nae) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if ("method".equals(pair.getNameAsString())) {
                        String value = pair.getValue().toString();
                        if (value.contains("GET")) return Optional.of("GET");
                        if (value.contains("POST")) return Optional.of("POST");
                        if (value.contains("PUT")) return Optional.of("PUT");
                        if (value.contains("DELETE")) return Optional.of("DELETE");
                        if (value.contains("PATCH")) return Optional.of("PATCH");
                    }
                }
            }
            return Optional.empty();
        }

        static List<String> extractPathVariables(MethodDeclaration n) {
            List<String> vars = new ArrayList<>();
            n.getParameters().forEach(param -> {
                param.getAnnotations().forEach(ann -> {
                    if ("PathVariable".equals(ann.getNameAsString())) {
                        String name = extractStringAttr(ann, "value")
                            .or(() -> extractStringAttr(ann, "name"))
                            .orElse(param.getNameAsString());
                        vars.add(name);
                    }
                });
            });
            return vars;
        }

        static List<String> extractQueryParams(MethodDeclaration n) {
            List<String> params = new ArrayList<>();
            n.getParameters().forEach(param -> {
                param.getAnnotations().forEach(ann -> {
                    if ("RequestParam".equals(ann.getNameAsString())) {
                        String name = extractStringAttr(ann, "value")
                            .or(() -> extractStringAttr(ann, "name"))
                            .orElse(param.getNameAsString());
                        params.add(name);
                    }
                });
            });
            return params;
        }
    }
}
