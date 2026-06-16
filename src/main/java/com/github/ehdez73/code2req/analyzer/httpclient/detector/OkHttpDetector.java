package com.github.ehdez73.code2req.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpClientType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OkHttpDetector implements HttpClientDetector {

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        method.getBody().ifPresent(body -> {
            String bodyText = body.toString();
            if (!bodyText.toLowerCase().contains("okhttp")) return;

            body.findAll(MethodCallExpr.class).forEach(mce -> {
                if (!"newCall".equals(mce.getNameAsString())) return;

                String httpMethod = findHttpMethod(bodyText);
                String url = findUrlFromText(bodyText);
                boolean isExpr = url.contains("${");

                result.add(new OutboundHttpCallInfo(
                    httpMethod, url, isExpr,
                    OutboundHttpClientType.OK_HTTP.name(),
                    methodName, className, filePath));
            });
        });
    }

    static String findHttpMethod(String bodyText) {
        String lower = bodyText.toLowerCase();
        if (lower.contains(".post(")) return "POST";
        if (lower.contains(".put(")) return "PUT";
        if (lower.contains(".delete(")) return "DELETE";
        if (lower.contains(".patch(")) return "PATCH";
        if (lower.contains(".head(")) return "HEAD";
        return "GET";
    }

    static String findUrlFromText(String bodyText) {
        int urlIdx = bodyText.indexOf(".url(");
        if (urlIdx < 0) return "";
        int start = urlIdx + 5;
        if (start >= bodyText.length()) return "";
        char quote = bodyText.charAt(start);
        if (quote != '"' && quote != '\'') {
            int endParen = bodyText.indexOf(')', start);
            return endParen > start ? bodyText.substring(start, endParen).trim() : "";
        }
        int end = bodyText.indexOf(quote, start + 1);
        return end > start ? bodyText.substring(start + 1, end) : "";
    }
}
