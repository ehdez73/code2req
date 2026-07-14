package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpClientType;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class HttpExchangeDetector implements HttpClientDetector {

    private static final List<String> EXCHANGE_ANNOTATIONS = List.of(
        "HttpExchange", "GetExchange", "PostExchange", "PutExchange",
        "DeleteExchange", "PatchExchange");

    @Override
    public void detectClass(List<OutboundHttpCallInfo> result, ClassOrInterfaceDeclaration clazz,
                             String className, String filePath) {
        Optional<AnnotationExpr> exchangeAnn = clazz.getAnnotationByName("HttpExchange");
        String baseUrl = "";
        if (exchangeAnn.isPresent()) {
            baseUrl = extractExchangeUrl(exchangeAnn.get());
        }

        for (MethodDeclaration method : clazz.getMethods()) {
            for (AnnotationExpr ann : method.getAnnotations()) {
                String annName = ann.getNameAsString();
                if (!EXCHANGE_ANNOTATIONS.contains(annName)) continue;

                String httpMethod = exchangeToHttpMethod(annName);
                String path = extractExchangeUrl(ann);
                String fullUrl = combineUrl(baseUrl, path);
                boolean isExpr = fullUrl.contains("${");

                result.add(new OutboundHttpCallInfo(
                    httpMethod, fullUrl, isExpr,
                    OutboundHttpClientType.HTTP_EXCHANGE.name(),
                    method.getNameAsString(), className, filePath));
            }
        }
    }

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
    }

    static String extractExchangeUrl(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("url".equals(pair.getNameAsString()) || "value".equals(pair.getNameAsString())
                    || "path".equals(pair.getNameAsString())) {
                    String val = pair.getValue().toString();
                    return val.replaceAll("^\"|\"$", "");
                }
            }
        }
        if (ann instanceof SingleMemberAnnotationExpr smae) {
            String val = smae.getMemberValue().toString();
            return val.replaceAll("^\"|\"$", "");
        }
        return "";
    }

    static String exchangeToHttpMethod(String annName) {
        return switch (annName) {
            case "GetExchange" -> "GET";
            case "PostExchange" -> "POST";
            case "PutExchange" -> "PUT";
            case "DeleteExchange" -> "DELETE";
            case "PatchExchange" -> "PATCH";
            default -> "GET";
        };
    }

    static String combineUrl(String base, String path) {
        if (base.isEmpty()) return path;
        if (path.isEmpty()) return base;
        String sep = base.endsWith("/") || path.startsWith("/") ? "" : "/";
        return base + sep + path;
    }
}
