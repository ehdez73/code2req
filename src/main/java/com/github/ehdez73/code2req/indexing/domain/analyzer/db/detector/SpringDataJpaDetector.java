package com.github.ehdez73.code2req.indexing.domain.analyzer.db.detector;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessHelper;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

@Component
public class SpringDataJpaDetector implements DbAccessDetector {

    @Override
    public void detectClass(List<DbAccessInfo> result, ClassOrInterfaceDeclaration clazz,
                            String className, String filePath) {
        if (!clazz.isInterface()) return;

        boolean isSpringDataRepo = clazz.getExtendedTypes().stream()
            .anyMatch(ext -> isSpringDataRepositoryType(ext, clazz));
        if (!isSpringDataRepo) return;

        String entityType = clazz.getExtendedTypes().stream()
            .filter(ext -> isSpringDataRepositoryType(ext, clazz))
            .map(this::extractEntityType)
            .filter(t -> !t.isEmpty())
            .findFirst().orElse("");

        boolean isSpringDataJdbc = clazz.findAncestor(CompilationUnit.class)
            .map(cu -> cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString()
                    .equals("org.springframework.data.jdbc.repository.query.Query")))
            .orElse(false);

        for (MethodDeclaration method : clazz.getMethods()) {
            String methodName = method.getNameAsString();
            int startLine = method.getBegin().map(r -> r.line).orElse(0);
            int endLine = method.getEnd().map(r -> r.line).orElse(0);

            Optional<AnnotationExpr> queryAnn = method.getAnnotationByName("Query");
            if (queryAnn.isPresent()) {
                String sql = extractQueryValue(queryAnn.get());
                boolean nativeQuery = isNativeQuery(queryAnn.get()) || isSpringDataJdbc;
                result.add(new DbAccessInfo(
                    nativeQuery ? DbAccessType.NATIVE_SQL.name() : DbAccessType.JPQL_HQL.name(),
                    sql, "", "",
                    methodName, className, filePath, entityType, false, startLine, endLine));
                continue;
            }

            if (isDerivedQueryMethod(methodName)) {
                result.add(new DbAccessInfo(
                    DbAccessType.SPRING_DATA.name(), "", "", "",
                    methodName, className, filePath, entityType, false, startLine, endLine));
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

    private static boolean isSpringDataRepositoryType(ClassOrInterfaceType ext, ClassOrInterfaceDeclaration clazz) {
        String name = ext.getNameAsString();

        boolean hasImport = clazz.findAncestor(CompilationUnit.class)
            .map(cu -> cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().endsWith("." + name)
                    && imp.getNameAsString().startsWith("org.springframework.data")))
            .orElse(false);
        if (hasImport) return true;

        return ext.getScope()
            .map(scope -> scope.toString().startsWith("org.springframework.data"))
            .orElse(false);
    }

    private static String extractQueryValue(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr smae) {
            return DbAccessHelper.extractStringLiteral(smae.getMemberValue());
        }
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("value".equals(pair.getNameAsString())) {
                    return DbAccessHelper.extractStringLiteral(pair.getValue());
                }
            }
        }
        return "";
    }

    private static boolean isNativeQuery(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("nativeQuery".equals(pair.getNameAsString())
                        && pair.getValue() instanceof BooleanLiteralExpr ble) {
                    return ble.getValue();
                }
            }
        }
        return false;
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
