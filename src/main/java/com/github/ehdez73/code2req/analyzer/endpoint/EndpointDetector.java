package com.github.ehdez73.code2req.analyzer.endpoint;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.List;

@FunctionalInterface
public interface EndpointDetector {

    void detect(List<EndpointInfo> result, MethodDeclaration method,
                String className, String filePath);

    default void detectClass(List<EndpointInfo> result, ClassOrInterfaceDeclaration clazz,
                              String className, String filePath) {}
}
