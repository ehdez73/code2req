package com.github.ehdez73.code2req.indexing.domain.analyzer.db;

import java.util.List;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

@FunctionalInterface
public interface DbAccessDetector {

    void detect(List<DbAccessInfo> result, MethodDeclaration method,
                String className, String filePath);

    default void detectClass(List<DbAccessInfo> result, ClassOrInterfaceDeclaration clazz,
                              String className, String filePath) {
    }
}
