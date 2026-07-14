package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpClientType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApacheHttpClientDetector implements HttpClientDetector {

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        method.getBody().ifPresent(body -> {
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();
                String scope = mce.getScope()
                    .map(Object::toString).orElse("").toLowerCase();

                if ("execute".equals(callName) && scope.contains("httpclient")) {
                    String httpMethod = extractMethodFromArg(mce);
                    String url = extractUrlFromArg(mce);
                    boolean isExpr = url.contains("${");
                    result.add(new OutboundHttpCallInfo(
                        httpMethod, url, isExpr,
                        OutboundHttpClientType.APACHE_HTTP.name(),
                        methodName, className, filePath));
                }
            });

            body.findAll(ObjectCreationExpr.class).forEach(oce -> {
                String typeName = oce.getTypeAsString();
                String httpMethod = switch (typeName) {
                    case "HttpGet" -> "GET";
                    case "HttpPost" -> "POST";
                    case "HttpPut" -> "PUT";
                    case "HttpDelete" -> "DELETE";
                    case "HttpPatch" -> "PATCH";
                    case "HttpHead" -> "HEAD";
                    case "HttpOptions" -> "OPTIONS";
                    default -> null;
                };
                if (httpMethod == null) return;

                if (!oce.getArguments().isEmpty()) {
                    String url = oce.getArgument(0).toString().replaceAll("^\"|\"$", "");
                    boolean isExpr = url.contains("${");
                    result.add(new OutboundHttpCallInfo(
                        httpMethod, url, isExpr,
                        OutboundHttpClientType.APACHE_HTTP.name(),
                        methodName, className, filePath));
                }
            });
        });
    }

    static String extractMethodFromArg(MethodCallExpr mce) {
        if (!mce.getArguments().isEmpty()) {
            String arg = mce.getArgument(0).toString();
            if (arg.contains("HttpGet") || arg.contains("HttpGet(")) return "GET";
            if (arg.contains("HttpPost") || arg.contains("HttpPost(")) return "POST";
            if (arg.contains("HttpPut") || arg.contains("HttpPut(")) return "PUT";
            if (arg.contains("HttpDelete") || arg.contains("HttpDelete(")) return "DELETE";
            if (arg.contains("HttpPatch") || arg.contains("HttpPatch(")) return "PATCH";
            if (arg.contains("HttpHead") || arg.contains("HttpHead(")) return "HEAD";
        }
        return "GET";
    }

    static String extractUrlFromArg(MethodCallExpr mce) {
        if (!mce.getArguments().isEmpty()) {
            String arg = mce.getArgument(0).toString();
            int paren = arg.indexOf('(');
            if (paren >= 0) {
                String inner = arg.substring(paren + 1);
                int close = inner.lastIndexOf(')');
                if (close >= 0) inner = inner.substring(0, close);
                return inner.replaceAll("^\"|\"$", "").trim();
            }
        }
        return "";
    }
}
