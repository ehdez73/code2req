package com.github.ehdez73.code2req.analyzer.httpclient.detector;

import com.github.ehdez73.code2req.analyzer.httpclient.HttpClientDetector;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpCallInfo;
import com.github.ehdez73.code2req.analyzer.httpclient.OutboundHttpClientType;
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
public class FeignClientDetector implements HttpClientDetector {

    private static final List<String> MAPPING_ANNOTATIONS = List.of(
        "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
        "PatchMapping", "RequestMapping");

    @Override
    public void detectClass(List<OutboundHttpCallInfo> result, ClassOrInterfaceDeclaration clazz,
                             String className, String filePath) {
        Optional<AnnotationExpr> feignAnn = clazz.getAnnotationByName("FeignClient");
        if (feignAnn.isEmpty()) return;

        String baseUrl = extractFeignUrl(feignAnn.get());
        boolean isExpr = baseUrl.contains("${");

        for (MethodDeclaration method : clazz.getMethods()) {
            for (AnnotationExpr ann : method.getAnnotations()) {
                String annName = ann.getNameAsString();
                if (!MAPPING_ANNOTATIONS.contains(annName)) continue;

                String httpMethod = mappingToHttpMethod(annName);
                String path = extractMappingPath(ann);
                String fullUrl = combineUrl(baseUrl, path);
                boolean fullIsExpr = isExpr || fullUrl.contains("${");

                result.add(new OutboundHttpCallInfo(
                    httpMethod, fullUrl, fullIsExpr,
                    OutboundHttpClientType.FEIGN_CLIENT.name(),
                    method.getNameAsString(), className, filePath));
            }
        }
    }

    @Override
    public void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                       String className, String filePath) {
    }

    static String extractFeignUrl(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("url".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString())) {
                    String val = pair.getValue().toString();
                    return val.replaceAll("^\"|\"$", "");
                }
            }
        }
        return "";
    }

    static String mappingToHttpMethod(String annName) {
        return switch (annName) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            case "RequestMapping" -> "REQUEST";
            default -> "GET";
        };
    }

    static String extractMappingPath(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (String attr : List.of("value", "path")) {
                for (MemberValuePair pair : nae.getPairs()) {
                    if (attr.equals(pair.getNameAsString())) {
                        String val = pair.getValue().toString();
                        return val.replaceAll("^\"|\"$", "");
                    }
                }
            }
        }
        if (ann instanceof SingleMemberAnnotationExpr smae) {
            String val = smae.getMemberValue().toString();
            return val.replaceAll("^\"|\"$", "");
        }
        return "";
    }

    static String combineUrl(String base, String path) {
        if (base.isEmpty()) return path;
        if (path.isEmpty()) return base;
        String sep = base.endsWith("/") || path.startsWith("/") ? "" : "/";
        return base + sep + path;
    }
}
