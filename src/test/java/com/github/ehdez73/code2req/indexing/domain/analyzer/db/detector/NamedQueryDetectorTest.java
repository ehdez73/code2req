package com.github.ehdez73.code2req.indexing.domain.analyzer.db.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NamedQueryDetectorTest {

    private final NamedQueryDetector detector = new NamedQueryDetector();

    private List<DbAccessInfo> detectClass(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<DbAccessInfo> result = new ArrayList<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz ->
            detector.detectClass(result, clazz, "MyEntity", "MyEntity.java"));
        return result;
    }

    @Test
    void detectsNamedQuery() {
        var result = detectClass("""
            import jakarta.persistence.NamedQuery;
            @NamedQuery(name = "findAll", query = "SELECT e FROM MyEntity e")
            class MyEntity {}
            """);
        assertEquals(1, result.size());
        assertEquals("JPQL_HQL", result.get(0).type());
        assertTrue(result.get(0).sql().contains("SELECT e FROM MyEntity e"));
    }

    @Test
    void detectsNamedNativeQuery() {
        var result = detectClass("""
            import jakarta.persistence.NamedNativeQuery;
            @NamedNativeQuery(name = "findAll", query = "SELECT * FROM my_entity")
            class MyEntity {}
            """);
        assertEquals(1, result.size());
        assertEquals("NATIVE_SQL", result.get(0).type());
    }

    @Test
    void detectsNamedQueriesContainer() {
        var result = detectClass("""
            import jakarta.persistence.NamedQueries;
            import jakarta.persistence.NamedQuery;
            @NamedQueries(value = {
                @NamedQuery(name = "findAll", query = "SELECT e FROM MyEntity e"),
                @NamedQuery(name = "findById", query = "SELECT e FROM MyEntity e WHERE e.id = :id")
            })
            class MyEntity {}
            """);
        assertEquals(2, result.size());
    }

    @Test
    void detectsNamedNativeQueriesContainer() {
        var result = detectClass("""
            import jakarta.persistence.NamedNativeQueries;
            import jakarta.persistence.NamedNativeQuery;
            @NamedNativeQueries(value = {
                @NamedNativeQuery(name = "findAll", query = "SELECT * FROM my_entity"),
                @NamedNativeQuery(name = "findById", query = "SELECT * FROM my_entity WHERE id = ?")
            })
            class MyEntity {}
            """);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "NATIVE_SQL".equals(r.type())));
    }

    @Test
    void ignoresNonQueryAnnotations() {
        var result = detectClass("""
            import jakarta.persistence.Entity;
            @Entity
            class MyEntity {}
            """);
        assertTrue(result.isEmpty());
    }

    @Test
    void handlesSingleMemberAnnotationValue() {
        var result = detectClass("""
            import jakarta.persistence.NamedQuery;
            @NamedQuery(name = "findAll", query = "FROM MyEntity")
            class MyEntity {}
            """);
        assertEquals(1, result.size());
    }

    @Test
    void methodDetectionDoesNothing() {
        var cu = StaticJavaParser.parse("class C { void m() {} }");
        List<DbAccessInfo> result = new ArrayList<>();
        cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "C", "C.java"));
        assertTrue(result.isEmpty());
    }
}
