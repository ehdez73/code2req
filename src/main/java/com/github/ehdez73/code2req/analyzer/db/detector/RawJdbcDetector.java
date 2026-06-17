package com.github.ehdez73.code2req.analyzer.db.detector;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.analyzer.db.DbAccessHelper;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.db.DbAccessType;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

@Component
public class RawJdbcDetector implements DbAccessDetector {

    private static final List<String> CONNECTION_METHODS = List.of(
        "prepareStatement", "prepareCall");

    private static final List<String> STATEMENT_EXEC_METHODS = List.of(
        "executeQuery", "executeUpdate", "execute", "executeLargeUpdate", "addBatch");

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
        String methodName = method.getNameAsString();
        method.getBody().ifPresent(body ->
            body.findAll(MethodCallExpr.class).forEach(mce -> {
                String callName = mce.getNameAsString();
                String scope = mce.getScope()
                    .map(Object::toString).orElse("").toLowerCase();

                if (CONNECTION_METHODS.contains(callName)
                        && isConnectionScope(scope)) {
                    String sql = DbAccessHelper.extractFirstStringArg(mce);
                    result.add(new DbAccessInfo(
                        DbAccessType.NATIVE_SQL.name(), sql,
                        DbAccessHelper.inferTableHint(sql), "",
                        methodName, className, filePath, "", false));
                }

                if (STATEMENT_EXEC_METHODS.contains(callName)
                        && !scope.contains("jdbctemplate")
                        && mce.getArguments().size() >= 1) {
                    String sql = DbAccessHelper.extractFirstStringArg(mce);
                    if (!sql.isEmpty()) {
                        result.add(new DbAccessInfo(
                            DbAccessType.NATIVE_SQL.name(), sql,
                            DbAccessHelper.inferTableHint(sql), "",
                            methodName, className, filePath, "", false));
                    }
                }
            })
        );
    }

    private static boolean isConnectionScope(String scope) {
        return scope.contains("connection") || scope.equals("conn")
            || scope.endsWith(".getconnection");
    }
}
