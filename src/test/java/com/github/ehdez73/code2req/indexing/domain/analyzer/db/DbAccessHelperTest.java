package com.github.ehdez73.code2req.indexing.domain.analyzer.db;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DbAccessHelperTest {

    @Test
    void extractStringLiteralFromStringLiteral() {
        var expr = StaticJavaParser.parseExpression("\"hello\"");
        assertEquals("hello", DbAccessHelper.extractStringLiteral(expr));
    }

    @Test
    void extractStringLiteralFromNameExpr() {
        var expr = StaticJavaParser.parseExpression("MY_CONSTANT");
        assertEquals("MY_CONSTANT", DbAccessHelper.extractStringLiteral(expr));
    }

    @Test
    void extractStringLiteralFromFieldAccess() {
        var expr = StaticJavaParser.parseExpression("SomeClass.CONSTANT");
        assertEquals("SomeClass.CONSTANT", DbAccessHelper.extractStringLiteral(expr));
    }

    @Test
    void extractStringLiteralFromOtherExpression() {
        var expr = StaticJavaParser.parseExpression("a + b");
        assertEquals("a + b", DbAccessHelper.extractStringLiteral(expr));
    }

    @Test
    void extractFirstStringArgReturnsEmptyWhenNoArgs() {
        var mce = StaticJavaParser.parseExpression("someMethod()").asMethodCallExpr();
        assertEquals("", DbAccessHelper.extractFirstStringArg(mce));
    }

    @Test
    void extractFirstStringArgWithStringLiteral() {
        var mce = StaticJavaParser.parseExpression("query(\"SELECT * FROM users\")").asMethodCallExpr();
        assertEquals("SELECT * FROM users", DbAccessHelper.extractFirstStringArg(mce));
    }

    @Test
    void inferTableHintFromSelect() {
        assertEquals("users", DbAccessHelper.inferTableHint("SELECT * FROM users WHERE id = 1"));
    }

    @Test
    void inferTableHintFromInsert() {
        assertEquals("orders", DbAccessHelper.inferTableHint("INSERT INTO orders (id) VALUES (1)"));
    }

    @Test
    void inferTableHintFromUpdate() {
        assertEquals("products", DbAccessHelper.inferTableHint("UPDATE products SET name = 'x'"));
    }

    @Test
    void inferTableHintReturnsEmptyForNull() {
        assertEquals("", DbAccessHelper.inferTableHint(null));
    }

    @Test
    void inferTableHintReturnsEmptyForBlank() {
        assertEquals("", DbAccessHelper.inferTableHint("  "));
    }

    @Test
    void inferTableHintReturnsEmptyWhenNoTableKeyword() {
        assertEquals("", DbAccessHelper.inferTableHint("SELECT 1"));
    }

    @Test
    void extractProcedureNameFromNormalAnnotation() {
        var ann = StaticJavaParser.parseAnnotation("@Procedure(name = \"my_proc\")");
        assertEquals("my_proc", DbAccessHelper.extractProcedureName(ann));
    }

    @Test
    void extractProcedureNameFromValueAttribute() {
        var ann = StaticJavaParser.parseAnnotation("@Procedure(\"my_proc\")");
        assertEquals("my_proc", DbAccessHelper.extractProcedureName(ann));
    }

    @Test
    void extractProcedureNameFromProcedureNameAttribute() {
        var ann = StaticJavaParser.parseAnnotation("@NamedStoredProcedureQuery(procedureName = \"my_proc\")");
        assertEquals("my_proc", DbAccessHelper.extractProcedureName(ann));
    }

    @Test
    void extractProcedureNameReturnsEmptyWhenNoMatch() {
        var ann = StaticJavaParser.parseAnnotation("@Entity");
        assertEquals("", DbAccessHelper.extractProcedureName(ann));
    }

    @Test
    void isQueryMethodDetectsCreateQuery() {
        assertTrue(DbAccessHelper.isQueryMethod("createQuery"));
    }

    @Test
    void isQueryMethodDetectsCreateNamedQuery() {
        assertTrue(DbAccessHelper.isQueryMethod("createNamedQuery"));
    }

    @Test
    void isQueryMethodDetectsCreateNativeQuery() {
        assertTrue(DbAccessHelper.isQueryMethod("createNativeQuery"));
    }

    @Test
    void isQueryMethodDetectsCreateSQLQuery() {
        assertTrue(DbAccessHelper.isQueryMethod("createSQLQuery"));
    }

    @Test
    void isQueryMethodReturnsFalseForOtherMethods() {
        assertFalse(DbAccessHelper.isQueryMethod("findAll"));
    }

    @Test
    void isJpqlQueryMethodReturnsTrueForCreateQuery() {
        assertTrue(DbAccessHelper.isJpqlQueryMethod("createQuery"));
    }

    @Test
    void isJpqlQueryMethodReturnsTrueForCreateNamedQuery() {
        assertTrue(DbAccessHelper.isJpqlQueryMethod("createNamedQuery"));
    }

    @Test
    void isNativeQueryMethodReturnsTrueForCreateNativeQuery() {
        assertTrue(DbAccessHelper.isNativeQueryMethod("createNativeQuery"));
    }

    @Test
    void isNativeQueryMethodReturnsTrueForCreateSQLQuery() {
        assertTrue(DbAccessHelper.isNativeQueryMethod("createSQLQuery"));
    }
}
