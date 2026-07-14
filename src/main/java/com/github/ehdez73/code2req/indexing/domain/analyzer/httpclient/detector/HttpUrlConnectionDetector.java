package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.OutboundHttpClientType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HttpUrlConnectionDetector implements HttpClientDetector {

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        String[] urlHolder = {""};
        List<String> httpMethods = new ArrayList<>();

        method.getBody().ifPresent(body -> {
            String bodyText = body.toString();

            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();

                if ("openConnection".equals(callName)) {
                    urlHolder[0] = findUrlInBody(bodyText);
                }

                if ("setRequestMethod".equalsIgnoreCase(callName)) {
                    if (!mce.getArguments().isEmpty()) {
                        httpMethods.add(mce.getArgument(0).toString().replaceAll("^\"|\"$", ""));
                    } else {
                        httpMethods.add("");
                    }
                }
            });

            String url = urlHolder[0];
            String httpMethod = httpMethods.isEmpty() ? "GET" : httpMethods.get(httpMethods.size() - 1);

            if (!url.isEmpty()) {
                boolean isExpr = url.contains("${");
                result.add(new OutboundHttpCallInfo(
                    httpMethod, url, isExpr,
                    OutboundHttpClientType.HTTP_URL_CONNECTION.name(),
                    methodName, className, filePath));
            }
        });
    }

    static String findUrlInBody(String bodyText) {
        int idx = bodyText.indexOf("new URL(");
        if (idx < 0) return "";
        int start = idx + 8;
        char quote = bodyText.charAt(start);
        if (quote != '"' && quote != '\'') {
            int endParen = bodyText.indexOf(')', start);
            return endParen > start ? bodyText.substring(start, endParen).trim() : "";
        }
        int end = bodyText.indexOf(quote, start + 1);
        return end > start ? bodyText.substring(start + 1, end) : "";
    }
}
