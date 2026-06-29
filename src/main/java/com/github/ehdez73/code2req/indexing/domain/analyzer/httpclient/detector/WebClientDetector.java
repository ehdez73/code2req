package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpClientType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WebClientDetector implements HttpClientDetector {

    private static final List<String> HTTP_VERB_METHODS = List.of(
        "get", "post", "put", "delete", "patch", "head", "options", "method");

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        method.getBody().ifPresent(body ->
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                if (!HTTP_VERB_METHODS.contains(mce.getNameAsString())) return;
                if (!scopeContainsWebClient(mce)) return;

                String httpMethod = inferHttpMethod(mce);
                String url = findUrlInChain(mce);
                boolean isExpr = url.contains("${");

                result.add(new OutboundHttpCallInfo(
                    httpMethod, url, isExpr,
                    OutboundHttpClientType.WEB_CLIENT.name(),
                    methodName, className, filePath));
            })
        );
    }

    private boolean scopeContainsWebClient(MethodCallExpr mce) {
        String scope = mce.getScope().map(Object::toString).orElse("").toLowerCase();
        if (scope.contains("webclient")) return true;
        var parent = mce.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof MethodCallExpr parentCall) {
                String ps = parentCall.getScope().map(Object::toString).orElse("").toLowerCase();
                if (ps.contains("webclient")) return true;
            }
            parent = parent.getParentNode().orElse(null);
        }
        return false;
    }

    static String inferHttpMethod(MethodCallExpr mce) {
        return switch (mce.getNameAsString()) {
            case "get" -> "GET";
            case "post" -> "POST";
            case "put" -> "PUT";
            case "delete" -> "DELETE";
            case "patch" -> "PATCH";
            case "head" -> "HEAD";
            case "options" -> "OPTIONS";
            case "method" -> extractMethodFromArg(mce);
            default -> "GET";
        };
    }

    static String extractMethodFromArg(MethodCallExpr mce) {
        if (!mce.getArguments().isEmpty()) {
            String arg = mce.getArgument(0).toString();
            if (arg.contains("GET")) return "GET";
            if (arg.contains("POST")) return "POST";
            if (arg.contains("PUT")) return "PUT";
            if (arg.contains("DELETE")) return "DELETE";
            if (arg.contains("PATCH")) return "PATCH";
        }
        return "GET";
    }

    static String findUrlInChain(MethodCallExpr verbCall) {
        var parent = verbCall.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof MethodCallExpr parentCall) {
                if ("uri".equals(parentCall.getNameAsString())) {
                    if (!parentCall.getArguments().isEmpty()) {
                        String u = parentCall.getArgument(0).toString();
                        return u.replaceAll("^\"|\"$", "");
                    }
                    return "";
                }
                parent = parentCall.getParentNode().orElse(null);
            } else {
                break;
            }
        }
        return "";
    }
}
