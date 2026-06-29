package com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ServletEndpointDetector implements EndpointDetector {

    private static final Map<String, String> METHOD_MAPPINGS = Map.of(
        "doGet", "GET",
        "doPost", "POST",
        "doPut", "PUT",
        "doDelete", "DELETE",
        "doPatch", "PATCH",
        "doHead", "HEAD",
        "doTrace", "TRACE",
        "doOptions", "OPTIONS"
    );

    private static final List<String> HTTP_SERVLET_TYPES = List.of(
        "HttpServlet",
        "javax.servlet.http.HttpServlet",
        "jakarta.servlet.http.HttpServlet"
    );

    private static final List<String> SERVLET_REQUEST_TYPES = List.of(
        "HttpServletRequest",
        "javax.servlet.http.HttpServletRequest",
        "jakarta.servlet.http.HttpServletRequest"
    );

    private static final List<String> SERVLET_RESPONSE_TYPES = List.of(
        "HttpServletResponse",
        "javax.servlet.http.HttpServletResponse",
        "jakarta.servlet.http.HttpServletResponse"
    );

    private static final List<String> WEB_SERVLET_NAMES = List.of(
        "WebServlet",
        "javax.servlet.annotation.WebServlet",
        "jakarta.servlet.annotation.WebServlet"
    );

    @Override
    public void detect(List<EndpointInfo> result, MethodDeclaration method,
                        String className, String filePath) {
        ClassOrInterfaceDeclaration clazz = method.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (clazz == null) return;

        if (!isHttpServletSubclass(clazz)) return;

        String methodName = method.getNameAsString();
        String httpMethod = METHOD_MAPPINGS.get(methodName);
        if (httpMethod == null) return;

        if (!hasServletSignature(method)) return;

        List<String> paths = extractServletPaths(clazz);
        if (paths.isEmpty()) {
            paths.add("");
        }

        for (String path : paths) {
            result.add(new EndpointInfo(httpMethod, path, className, List.of(), List.of(), filePath, false, ""));
        }
    }

    private static boolean isHttpServletSubclass(ClassOrInterfaceDeclaration clazz) {
        return clazz.getExtendedTypes().stream()
            .anyMatch(t -> HTTP_SERVLET_TYPES.contains(t.getNameAsString()));
    }

    private static boolean hasServletSignature(MethodDeclaration method) {
        boolean hasRequest = false;
        boolean hasResponse = false;
        for (var param : method.getParameters()) {
            String typeStr = param.getType().toString();
            if (SERVLET_REQUEST_TYPES.contains(typeStr)) hasRequest = true;
            if (SERVLET_RESPONSE_TYPES.contains(typeStr)) hasResponse = true;
        }
        return hasRequest && hasResponse;
    }

    private static List<String> extractServletPaths(ClassOrInterfaceDeclaration clazz) {
        for (AnnotationExpr ann : clazz.getAnnotations()) {
            String annName = ann.getNameAsString();
            if (WEB_SERVLET_NAMES.contains(annName)) {
                List<String> patterns = new ArrayList<>();

                if (ann instanceof NormalAnnotationExpr nae) {
                    for (MemberValuePair pair : nae.getPairs()) {
                        String attrName = pair.getNameAsString();
                        if ("value".equals(attrName) || "urlPatterns".equals(attrName)) {
                            patterns.addAll(extractStringValues(pair.getValue()));
                        }
                    }
                } else if (ann instanceof SingleMemberAnnotationExpr smae) {
                    String rawValue = smae.getMemberValue().toString().replaceAll("^\"|\"$", "");
                    if (!rawValue.isEmpty()) {
                        patterns.add(rawValue);
                    }
                }

                if (!patterns.isEmpty()) {
                    return patterns.stream()
                        .map(p -> p.startsWith("/") ? p : "/" + p)
                        .toList();
                }
            }
        }
        return List.of();
    }

    private static List<String> extractStringValues(Expression expr) {
        List<String> values = new ArrayList<>();
        if (expr instanceof StringLiteralExpr sle) {
            values.add(sle.asString());
        } else if (expr instanceof ArrayInitializerExpr aie) {
            for (var val : aie.getValues()) {
                if (val instanceof StringLiteralExpr sle) {
                    values.add(sle.asString());
                }
            }
        } else {
            String raw = expr.toString().replaceAll("^\"|\"$", "");
            if (!raw.isEmpty() && !raw.startsWith("{")) {
                values.add(raw);
            }
        }
        return values;
    }
}
