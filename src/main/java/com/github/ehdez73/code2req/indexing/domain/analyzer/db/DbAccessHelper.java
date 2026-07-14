package com.github.ehdez73.code2req.indexing.domain.analyzer.db;

import java.util.regex.Pattern;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

public final class DbAccessHelper {

    private static final Pattern SQL_TABLE_PATTERN =
        Pattern.compile("(?i)\\b(?:FROM|INTO|UPDATE)\\s+(\\w+)");

    private DbAccessHelper() {
    }

    public static String extractStringLiteral(Expression arg) {
        if (arg.isStringLiteralExpr()) {
            return arg.asStringLiteralExpr().getValue();
        }
        if (arg instanceof NameExpr) {
            return arg.toString();
        }
        if (arg instanceof FieldAccessExpr) {
            return arg.toString();
        }
        return arg.toString();
    }

    public static String extractFirstStringArg(MethodCallExpr mce) {
        if (mce.getArguments().isEmpty()) return "";
        return extractStringLiteral(mce.getArguments().getFirst().get());
    }

    public static String inferTableHint(String sql) {
        if (sql == null || sql.isBlank()) return "";
        var matcher = SQL_TABLE_PATTERN.matcher(sql);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String extractProcedureName(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                String name = pair.getNameAsString();
                if ("name".equals(name) || "value".equals(name)
                        || "procedureName".equals(name)) {
                    return extractStringLiteral(pair.getValue());
                }
            }
        }
        if (ann instanceof SingleMemberAnnotationExpr smae) {
            return extractStringLiteral(smae.getMemberValue());
        }
        return "";
    }

    public static boolean isQueryMethod(String methodName) {
        return isJpqlQueryMethod(methodName) || isNativeQueryMethod(methodName);
    }

    public static boolean isJpqlQueryMethod(String methodName) {
        return "createQuery".equals(methodName)
            || "createNamedQuery".equals(methodName);
    }

    public static boolean isNativeQueryMethod(String methodName) {
        return "createNativeQuery".equals(methodName)
            || "createSQLQuery".equals(methodName);
    }
}
