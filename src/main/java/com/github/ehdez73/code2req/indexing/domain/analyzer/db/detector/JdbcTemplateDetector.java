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
public class JdbcTemplateDetector implements DbAccessDetector {

    private static final List<String> JDBC_SCOPES = List.of(
        "jdbctemplate", "npjt");

    private static final List<String> QUERY_METHODS = List.of(
        "query", "queryForObject", "queryForList", "queryForMap", "queryForRowSet");
    private static final List<String> UPDATE_METHODS = List.of(
        "update", "batchUpdate", "execute");

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        int startLine = method.getBegin().map(r -> r.line).orElse(0);
        int endLine = method.getEnd().map(r -> r.line).orElse(0);
        method.getBody().ifPresent(body ->
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();
                String scope = mce.getScope()
                    .map(Object::toString).orElse("").toLowerCase();
                if (JDBC_SCOPES.stream().noneMatch(scope::contains)) return;

                if (QUERY_METHODS.contains(callName)) {
                    String sql = DbAccessHelper.extractFirstStringArg(mce);
                    result.add(new DbAccessInfo(
                        DbAccessType.JDBC_TEMPLATE_QUERY.name(), sql,
                        DbAccessHelper.inferTableHint(sql), "",
                        methodName, className, filePath, "", false, startLine, endLine));
                } else if (UPDATE_METHODS.contains(callName)) {
                    String sql = DbAccessHelper.extractFirstStringArg(mce);
                    result.add(new DbAccessInfo(
                        DbAccessType.JDBC_TEMPLATE_UPDATE.name(), sql,
                        DbAccessHelper.inferTableHint(sql), "",
                        methodName, className, filePath, "", false, startLine, endLine));
                }
            })
        );
    }
}
