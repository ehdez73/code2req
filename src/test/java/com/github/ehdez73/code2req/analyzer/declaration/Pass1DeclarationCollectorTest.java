package com.github.ehdez73.code2req.analyzer.declaration;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Pass1DeclarationCollectorTest {

    private final Pass1DeclarationCollector collector = new Pass1DeclarationCollector();

    @Test
    void collectsClassAndMethodDeclarations() {
        CompilationUnit cu = StaticJavaParser.parse("""
            package com.example;
            public class OrderService {
                public void createOrder(String name) {}
                public Order find(Long id) { return null; }
            }
            """);

        var registry = new GlobalDeclarationRegistry();
        collector.collect(cu, registry, "OrderService.java");

        var decls = registry.findByClassName("OrderService");
        assertFalse(decls.isEmpty());

        var methods = registry.findMethod("OrderService", "createOrder", 1);
        assertEquals(1, methods.size());
        assertEquals("OrderService", methods.getFirst().className());
        assertEquals("createOrder", methods.getFirst().methodName());
        assertEquals(List.of("String"), methods.getFirst().paramTypes());
    }

    @Test
    void collectsEmptyMethodListForEmptyClass() {
        CompilationUnit cu = StaticJavaParser.parse("""
            package com.example;
            public class EmptyClass {}
            """);

        var registry = new GlobalDeclarationRegistry();
        collector.collect(cu, registry, "Empty.java");

        var decls = registry.findByClassName("EmptyClass");
        assertEquals(1, decls.size());
        assertEquals("<clinit>", decls.getFirst().methodName());
    }

    @Test
    void skipsFileOnParseError() {
        var registry = new GlobalDeclarationRegistry();
        // Should not throw — gracefull degradation
        assertDoesNotThrow(() -> {
            collector.collect(null, registry, "bad.java");
        });
    }

    @Test
    void collectsMultipleClasses() {
        CompilationUnit cu = StaticJavaParser.parse("""
            package com.example;
            class Foo { public void doFoo() {} }
            class Bar { public void doBar(String s) {} }
            """);

        var registry = new GlobalDeclarationRegistry();
        collector.collect(cu, registry, "multi.java");

        assertTrue(registry.hasClass("Foo"));
        assertTrue(registry.hasClass("Bar"));
        assertEquals(1, registry.findMethod("Foo", "doFoo", 0).size());
        assertEquals(1, registry.findMethod("Bar", "doBar", 1).size());
    }

    @Test
    void handlesInterfaceDeclaration() {
        CompilationUnit cu = StaticJavaParser.parse("""
            package com.example;
            interface MyService {
                void execute(String input);
            }
            """);

        var registry = new GlobalDeclarationRegistry();
        collector.collect(cu, registry, "MyService.java");

        assertTrue(registry.hasClass("MyService"));
        assertEquals(1, registry.findMethod("MyService", "execute", 1).size());
    }
}
