package com.github.ehdez73.code2req.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpClientType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RestTemplateDetector implements HttpClientDetector {

    private static final List<String> HTTP_METHODS = List.of(
        "getForObject", "getForEntity",
        "postForObject", "postForEntity",
        "put", "patchForObject",
        "delete",
        "exchange", "execute");

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        method.getBody().ifPresent(body ->
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();
                String scope = mce.getScope()
                    .map(Object::toString).orElse("").toLowerCase();
                if (!scope.contains("resttemplate")) return;
                if (!HTTP_METHODS.contains(callName)) return;

                String httpMethod = inferHttpMethod(callName, mce);
                String url = extractUrl(mce);
                boolean isExpr = url.contains("${");

                result.add(new OutboundHttpCallInfo(
                    httpMethod, url, isExpr,
                    OutboundHttpClientType.REST_TEMPLATE.name(),
                    methodName, className, filePath));
            })
        );
    }

    static String inferHttpMethod(String callName, MethodCallExpr mce) {
        return switch (callName) {
            case "getForObject", "getForEntity" -> "GET";
            case "postForObject", "postForEntity" -> "POST";
            case "put" -> "PUT";
            case "delete" -> "DELETE";
            case "patchForObject" -> "PATCH";
            case "exchange" -> extractHttpMethodFromExchange(mce);
            case "execute" -> "EXECUTE";
            default -> "GET";
        };
    }

    static String extractHttpMethodFromExchange(MethodCallExpr mce) {
        if (mce.getArguments().size() >= 2) {
            String second = mce.getArgument(1).toString();
            if (second.contains("GET")) return "GET";
            if (second.contains("POST")) return "POST";
            if (second.contains("PUT")) return "PUT";
            if (second.contains("DELETE")) return "DELETE";
            if (second.contains("PATCH")) return "PATCH";
            if (second.contains("HEAD")) return "HEAD";
            if (second.contains("OPTIONS")) return "OPTIONS";
        }
        return "EXCHANGE";
    }

    static String extractUrl(MethodCallExpr mce) {
        if (mce.getArguments().isEmpty()) return "";
        String first = mce.getArgument(0).toString();
        return first.replaceAll("^\"|\"$", "");
    }
}
