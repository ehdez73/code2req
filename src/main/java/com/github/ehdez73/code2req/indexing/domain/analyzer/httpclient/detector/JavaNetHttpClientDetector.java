package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpClientType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JavaNetHttpClientDetector implements HttpClientDetector {

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        method.getBody().ifPresent(body ->
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();
                String scope = mce.getScope()
                    .map(Object::toString).orElse("").toLowerCase();

                if (scope.contains("httpclient") && ("send".equals(callName) || "sendAsync".equals(callName))) {
                    String httpMethod = extractMethodFromHttpRequest(scope, mce, method);
                    String url = extractUrlFromHttpRequest(mce, method);
                    boolean isExpr = url.contains("${");
                    result.add(new OutboundHttpCallInfo(
                        httpMethod, url, isExpr,
                        OutboundHttpClientType.JAVA_NET_HTTP.name(),
                        methodName, className, filePath));
                    return;
                }

                if (scope.contains("httprequest") || scope.contains("httprequest.newbuilder")) {
                    if ("uri".equals(callName)) {
                        String httpMethod = extractMethodFromHttpRequest(scope, mce, method);
                        String url = extractMethodCallUri(mce);
                        boolean isExpr = url.contains("${");
                        result.add(new OutboundHttpCallInfo(
                            httpMethod, url, isExpr,
                            OutboundHttpClientType.JAVA_NET_HTTP.name(),
                            methodName, className, filePath));
                    }
                }
            })
        );
    }

    static String extractUrlFromHttpRequest(MethodCallExpr sendCall, MethodDeclaration method) {
        if (!sendCall.getArguments().isEmpty()) {
            String firstArg = sendCall.getArgument(0).toString();
            String lower = firstArg.toLowerCase();
            if (firstArg.contains("(") || lower.contains("builder") || lower.contains("newrequest")) {
                return extractUriFromBuilder(firstArg, method);
            }
            return firstArg.replaceAll("^\"|\"$", "");
        }
        return "";
    }

    static String extractUriFromBuilder(String builderExpr, MethodDeclaration method) {
        String[] uri = {""};
        method.getBody().ifPresent(body -> {
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String expr = mce.toString();
                if (expr.contains(".uri(") && builderExpr.contains(mce.getScope()
                        .map(Object::toString).orElse(""))) {
                    if (!mce.getArguments().isEmpty()) {
                        String u = mce.getArgument(0).toString();
                        uri[0] = u.replaceAll("^\"|\"$", "");
                    }
                }
            });
        });
        return uri[0];
    }

    static String extractMethodCallUri(MethodCallExpr uriCall) {
        if (!uriCall.getArguments().isEmpty()) {
            String u = uriCall.getArgument(0).toString();
            return u.replaceAll("^\"|\"$", "");
        }
        return "";
    }

    static String extractMethodFromHttpRequest(String scope, MethodCallExpr mce,
                                                MethodDeclaration method) {
        if (scope.contains(".get(") || scope.contains(".GET(")) return "GET";
        if (scope.contains(".post(") || scope.contains(".POST(")) return "POST";
        if (scope.contains(".put(") || scope.contains(".PUT(")) return "PUT";
        if (scope.contains(".delete(") || scope.contains(".DELETE(")) return "DELETE";

        String methodStr = method.toString().toLowerCase();
        if (methodStr.contains("httpmethod.get") || scope.contains("GET")) return "GET";
        if (methodStr.contains("httpmethod.post") || scope.contains("POST")) return "POST";
        if (methodStr.contains("httpmethod.put") || scope.contains("PUT")) return "PUT";
        if (methodStr.contains("httpmethod.delete") || scope.contains("DELETE")) return "DELETE";

        return "GET";
    }
}
