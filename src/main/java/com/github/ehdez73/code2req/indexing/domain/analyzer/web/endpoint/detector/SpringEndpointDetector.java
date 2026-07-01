package com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SpringEndpointDetector implements EndpointDetector {

    private static final Map<String, String> METHOD_MAPPINGS = Map.of(
        "GetMapping", "GET",
        "PostMapping", "POST",
        "PutMapping", "PUT",
        "DeleteMapping", "DELETE",
        "PatchMapping", "PATCH"
    );

    @Override
    public void detect(List<EndpointInfo> result, MethodDeclaration method,
                        String className, String filePath) {
        ClassOrInterfaceDeclaration clazz = method.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (clazz == null) return;

        if (!isSpringController(clazz)) return;

        String classLevelPath = extractClassLevelPath(clazz);
        boolean isRestController = hasAnnotation(clazz, "RestController");

        for (AnnotationExpr ann : method.getAnnotations()) {
            String name = ann.getNameAsString();
            String httpMethod = METHOD_MAPPINGS.get(name);
            if (httpMethod == null && "RequestMapping".equals(name)) {
                httpMethod = extractMethodAttr(ann).orElse("");
            }
            if (httpMethod == null) continue;

            String methodPath = extractStringAttr(ann, "value")
                .or(() -> extractStringAttr(ann, "path"))
                .orElse("");
            String fullPath = combinePaths(classLevelPath, methodPath);
            List<String> pathVars = extractPathVariables(method);
            List<String> queryParams = extractQueryParams(method);
            List<String> requestBodies = extractRequestBodies(method);

            boolean servesView = false;
            String viewName = "";
            if (!isRestController && !hasResponseBody(method)) {
                servesView = isViewReturn(method);
                if (servesView) {
                    viewName = extractViewName(method);
                }
            }

            result.add(new EndpointInfo(httpMethod, fullPath, className, method.getNameAsString(), pathVars, queryParams, filePath, servesView, viewName, requestBodies));
        }
    }

    private static boolean isSpringController(ClassOrInterfaceDeclaration clazz) {
        return hasAnnotation(clazz, "Controller") || hasAnnotation(clazz, "RestController");
    }

    private static boolean hasAnnotation(ClassOrInterfaceDeclaration clazz, String name) {
        return clazz.getAnnotations().stream().anyMatch(a -> name.equals(a.getNameAsString()));
    }

    private static boolean hasResponseBody(MethodDeclaration n) {
        return n.getAnnotations().stream()
            .anyMatch(a -> "ResponseBody".equals(a.getNameAsString()));
    }

    private static String extractClassLevelPath(ClassOrInterfaceDeclaration clazz) {
        for (AnnotationExpr ann : clazz.getAnnotations()) {
            if ("RequestMapping".equals(ann.getNameAsString())) {
                return extractStringAttr(ann, "value")
                    .or(() -> extractStringAttr(ann, "path"))
                    .orElse("");
            }
        }
        return "";
    }

    static boolean isViewReturn(MethodDeclaration n) {
        String returnType = n.getType().toString();
        return "ModelAndView".equals(returnType)
            || returnType.endsWith(".ModelAndView")
            || "String".equals(returnType)
            || "View".equals(returnType)
            || returnType.endsWith(".View")
            || "void".equals(returnType);
    }

    static String extractViewName(MethodDeclaration n) {
        return n.getBody()
            .map(body -> {
                for (var stmt : body.getStatements()) {
                    if (stmt instanceof ReturnStmt ret) {
                        var expr = ret.getExpression();
                        if (expr.isPresent()) {
                            var e = expr.get();
                            if (e instanceof StringLiteralExpr sle) {
                                return sle.asString();
                            }
                            if (e instanceof ObjectCreationExpr oce) {
                                String typeName = oce.getTypeAsString();
                                if ("ModelAndView".equals(typeName) || typeName.endsWith(".ModelAndView")) {
                                    var args = oce.getArguments();
                                    if (!args.isEmpty() && args.get(0) instanceof StringLiteralExpr sle) {
                                        return sle.asString();
                                    }
                                }
                            }
                        }
                    }
                }
                return "";
            })
            .orElse("");
    }

    public static String combinePaths(String classPath, String methodPath) {
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

    static List<String> extractRequestBodies(MethodDeclaration n) {
        List<String> bodies = new ArrayList<>();
        n.getParameters().forEach(param -> {
            param.getAnnotations().forEach(ann -> {
                if ("RequestBody".equals(ann.getNameAsString())) {
                    bodies.add(param.getType().toString());
                }
            });
        });
        return bodies;
    }
}
