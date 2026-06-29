package com.github.ehdez73.code2req.indexing.domain.analyzer.db.detector;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessType;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class TransactionalDetector implements DbAccessDetector {

    @Override
    public void detectClass(List<DbAccessInfo> result, ClassOrInterfaceDeclaration clazz,
                            String className, String filePath) {
        if (clazz.isInterface()) return;
        boolean classHasTx = clazz.getAnnotations().stream()
            .anyMatch(a -> "Transactional".equals(a.getNameAsString()));
        if (!classHasTx) return;

        var methodsWithOwnTx = new java.util.HashSet<>(clazz.getMethods());
        clazz.getMethods().stream()
            .filter(m -> !m.getAnnotationByName("Transactional").isPresent())
            .forEach(methodsWithOwnTx::remove);

        for (MethodDeclaration method : clazz.getMethods()) {
            if (!method.isPublic()) continue;
            if (methodsWithOwnTx.contains(method)) continue;
            result.add(new DbAccessInfo(
                DbAccessType.TRANSACTIONAL.name(), "", "", "",
                method.getNameAsString(), className, filePath, "", true));
        }
    }

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        if (!method.getAnnotationByName("Transactional").isPresent()) return;
        result.add(new DbAccessInfo(
            DbAccessType.TRANSACTIONAL.name(), "", "", "",
            method.getNameAsString(), className, filePath, "", true));
    }
}
