package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.List;

@FunctionalInterface
public interface HttpClientDetector {

    void detect(List<OutboundHttpCallInfo> result, MethodDeclaration method,
                String className, String filePath);

    default void detectClass(List<OutboundHttpCallInfo> result, ClassOrInterfaceDeclaration clazz,
                              String className, String filePath) {}
}
