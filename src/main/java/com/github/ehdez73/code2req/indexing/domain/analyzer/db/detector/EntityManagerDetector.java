package com.github.ehdez73.code2req.indexing.domain.analyzer.db.detector;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessHelper;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

@Component
public class EntityManagerDetector implements DbAccessDetector {

    private static final List<String> EM_METHODS = List.of(
        "persist", "merge", "find", "remove", "refresh", "detach", "clear",
        "createQuery", "createNamedQuery", "createNativeQuery",
        "createStoredProcedureQuery");

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        method.getBody().ifPresent(body ->
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();
                if (!EM_METHODS.contains(callName)) return;
                String scope = mce.getScope()
                    .map(Object::toString).orElse("").toLowerCase();
                if (!scope.contains("entitymanager") && !scope.equals("em")) return;

                String sql = DbAccessHelper.isQueryMethod(callName)
                    ? DbAccessHelper.extractFirstStringArg(mce) : "";
                String type;
                if (DbAccessHelper.isJpqlQueryMethod(callName)) {
                    type = DbAccessType.JPQL_HQL.name();
                } else if (DbAccessHelper.isNativeQueryMethod(callName)) {
                    type = DbAccessType.NATIVE_SQL.name();
                } else {
                    type = DbAccessType.ENTITY_MANAGER.name();
                }
                result.add(new DbAccessInfo(
                    type, sql, "", "",
                    methodName, className, filePath, "", false));
            })
        );
    }
}
