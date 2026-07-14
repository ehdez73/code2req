package com.github.ehdez73.code2req.indexing.domain.analyzer.db.detector;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessDetector;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessHelper;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessType;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

@Component
public class NamedQueryDetector implements DbAccessDetector {

    @Override
    public void detect(List<DbAccessInfo> result, MethodDeclaration method,
                       String className, String filePath) {
    }

    @Override
    public void detectClass(List<DbAccessInfo> result, ClassOrInterfaceDeclaration clazz,
                            String className, String filePath) {
        for (AnnotationExpr ann : clazz.getAnnotations()) {
            String name = ann.getNameAsString();
            switch (name) {
                case "NamedQuery" -> handleNamedQuery(result, ann, false, className, filePath);
                case "NamedNativeQuery" -> handleNamedQuery(result, ann, true, className, filePath);
                case "NamedQueries" -> handleContainer(result, ann, false, className, filePath);
                case "NamedNativeQueries" -> handleContainer(result, ann, true, className, filePath);
            }
        }
    }

    private void handleContainer(List<DbAccessInfo> result, AnnotationExpr ann,
                                  boolean nativeSql, String className, String filePath) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("value".equals(pair.getNameAsString())) {
                    Expression val = pair.getValue();
                    if (val instanceof ArrayInitializerExpr aie) {
                        for (Expression elem : aie.getValues()) {
                            if (elem instanceof AnnotationExpr ae) {
                                handleNamedQuery(result, ae, nativeSql, className, filePath);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleNamedQuery(List<DbAccessInfo> result, AnnotationExpr ann,
                                   boolean nativeSql, String className, String filePath) {
        String query = extractQueryFromAnnotation(ann);
        if (!query.isEmpty()) {
            String type = nativeSql ? DbAccessType.NATIVE_SQL.name() : DbAccessType.JPQL_HQL.name();
            String methodName = extractNameFromAnnotation(ann);
            result.add(new DbAccessInfo(
                type, query, "", "",
                methodName, className, filePath, "", false));
        }
    }

    private static String extractQueryFromAnnotation(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("query".equals(pair.getNameAsString())) {
                    return DbAccessHelper.extractStringLiteral(pair.getValue());
                }
            }
        }
        return "";
    }

    private static String extractNameFromAnnotation(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("name".equals(pair.getNameAsString())) {
                    return DbAccessHelper.extractStringLiteral(pair.getValue());
                }
            }
        }
        return "";
    }
}
