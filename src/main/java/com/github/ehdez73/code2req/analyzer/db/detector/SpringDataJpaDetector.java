package com.github.ehdez73.code2req.analyzer.db.detector;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.db.DbAccessType;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

@Component
public class SpringDataJpaDetector implements DbAccessDetector {

    private static final List<String> SPRING_DATA_REPOS = List.of(
        "JpaRepository", "CrudRepository", "PagingAndSortingRepository",
        "MongoRepository", "ReactiveCrudRepository", "ReactiveMongoRepository",
        "R2dbcRepository", "Neo4jRepository");

    @Override
    public void detectClass(List<DbAccessInfo> result, ClassOrInterfaceDeclaration clazz,
                            String className, String filePath) {
        if (!clazz.isInterface()) return;

        boolean isSpringDataRepo = clazz.getExtendedTypes().stream()
            .anyMatch(ext -> SPRING_DATA_REPOS.contains(ext.getNameAsString()));
        if (!isSpringDataRepo) return;

        String entityType = clazz.getExtendedTypes().stream()
            .filter(ext -> SPRING_DATA_REPOS.contains(ext.getNameAsString()))
            .map(this::extractEntityType)
            .filter(t -> !t.isEmpty())
            .findFirst().orElse("");

        for (MethodDeclaration method : clazz.getMethods()) {
            if (method.getAnnotationByName("Query").isPresent()) continue;

            String methodName = method.getNameAsString();
            if (isDerivedQueryMethod(methodName)) {
                result.add(new DbAccessInfo(
                    DbAccessType.SPRING_DATA.name(), "", "", "",
                    methodName, className, filePath, entityType, false));
            }
        }
    }

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
    }

    private String extractEntityType(ClassOrInterfaceType ext) {
        return ext.getTypeArguments()
            .filter(ta -> !ta.isEmpty())
            .map(ta -> ta.get(0).toString())
            .orElse("");
    }

    private static boolean isDerivedQueryMethod(String methodName) {
        if ("findAll".equals(methodName) || "findById".equals(methodName)
                || "save".equals(methodName) || "saveAll".equals(methodName)
                || "delete".equals(methodName) || "deleteById".equals(methodName)
                || "count".equals(methodName) || "existsById".equals(methodName)) {
            return true;
        }
        return methodName.startsWith("find") || methodName.startsWith("get")
            || methodName.startsWith("query") || methodName.startsWith("read")
            || methodName.startsWith("count") || methodName.startsWith("delete")
            || methodName.startsWith("remove");
    }
}
