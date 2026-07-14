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
public class HibernateSessionDetector implements DbAccessDetector {

    private static final List<String> SESSION_METHODS = List.of(
        "save", "saveOrUpdate", "update", "merge", "persist",
        "get", "load", "find", "byId", "byNaturalId",
        "delete", "remove", "replicate",
        "createQuery", "createNativeQuery", "createSQLQuery", "createCriteria");

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        int startLine = method.getBegin().map(r -> r.line).orElse(0);
        int endLine = method.getEnd().map(r -> r.line).orElse(0);
        method.getBody().ifPresent(body ->
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();
                if (!SESSION_METHODS.contains(callName)) return;
                String scope = mce.getScope()
                    .map(Object::toString).orElse("").toLowerCase();
                if (!scope.contains("session")) return;

                String sql = DbAccessHelper.isQueryMethod(callName)
                    ? DbAccessHelper.extractFirstStringArg(mce) : "";
                String type;
                if (DbAccessHelper.isJpqlQueryMethod(callName)) {
                    type = DbAccessType.JPQL_HQL.name();
                } else if (DbAccessHelper.isNativeQueryMethod(callName)) {
                    type = DbAccessType.NATIVE_SQL.name();
                } else {
                    type = DbAccessType.HIBERNATE_SESSION.name();
                }
                result.add(new DbAccessInfo(
                    type, sql, "", "",
                    methodName, className, filePath, "", false, startLine, endLine, 0));
            })
        );
    }
}
