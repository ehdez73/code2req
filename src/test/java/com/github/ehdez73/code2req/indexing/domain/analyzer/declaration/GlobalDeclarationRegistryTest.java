package com.github.ehdez73.code2req.indexing.domain.analyzer.declaration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalDeclarationRegistryTest {

    @Test
    void registerAndFindByClassName() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("OrderService", "createOrder", List.of("OrderDto"), "OrderService.java"));
        registry.register(new DeclarationInfo("OrderService", "find", List.of("String"), "OrderService.java"));

        var found = registry.findByClassName("OrderService");
        assertEquals(2, found.size());
    }

    @Test
    void findMethodByExactParamCount() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("OrderService", "find", List.of("String"), "OrderService.java"));
        registry.register(new DeclarationInfo("OrderService", "find", List.of("String", "String"), "OrderService.java"));

        var singleParam = registry.findMethod("OrderService", "find", 1);
        assertEquals(1, singleParam.size());
        assertEquals("String", singleParam.getFirst().paramTypes().getFirst());

        var twoParams = registry.findMethod("OrderService", "find", 2);
        assertEquals(1, twoParams.size());
    }

    @Test
    void findMethodReturnsEmptyForUnknownClass() {
        var registry = new GlobalDeclarationRegistry();
        assertTrue(registry.findMethod("Unknown", "foo", 0).isEmpty());
    }

    @Test
    void hasClass() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("OrderService", "find", List.of(), "OS.java"));

        assertTrue(registry.hasClass("OrderService"));
        assertFalse(registry.hasClass("Unknown"));
    }

    @Test
    void isEmptyOnCreation() {
        var registry = new GlobalDeclarationRegistry();
        assertTrue(registry.isEmpty());
    }

    @Test
    void freezePreventsRegistration() {
        var registry = new GlobalDeclarationRegistry();
        registry.freeze();
        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () ->
            registry.register(new DeclarationInfo("X", "y", List.of(), "x.java")));
    }

    @Test
    void allDeclarationsReturnsUnmodifiableSnapshot() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("A", "foo", List.of(), "a.java"));

        var all = registry.allDeclarations();
        assertEquals(1, all.get("A").size());
        assertThrows(UnsupportedOperationException.class, () -> all.put("B", List.of()));
    }

    @Test
    void sizeReflectsTotalDeclarations() {
        var registry = new GlobalDeclarationRegistry();
        assertEquals(0, registry.size());
        registry.register(new DeclarationInfo("A", "foo", List.of(), "a.java"));
        assertEquals(1, registry.size());
        registry.register(new DeclarationInfo("A", "bar", List.of("String"), "a.java"));
        assertEquals(2, registry.size());
    }

    @Test
    void findMethodsReturnsAllOverloads() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("OrderService", "find", List.of("String"), "OrderService.java"));
        registry.register(new DeclarationInfo("OrderService", "find", List.of("String", "String"), "OrderService.java"));

        var result = registry.findMethods("OrderService", "find");
        assertEquals(2, result.size());
    }

    @Test
    void findMethodsReturnsEmptyForUnknownClass() {
        var registry = new GlobalDeclarationRegistry();
        assertTrue(registry.findMethods("Unknown", "foo").isEmpty());
    }

    @Test
    void findMethodsReturnsEmptyForUnknownMethod() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("A", "bar", List.of(), "a.java"));
        assertTrue(registry.findMethods("A", "unknown").isEmpty());
    }

    @Test
    void registerSuperTypeAndGetSuperTypes() {
        var registry = new GlobalDeclarationRegistry();
        registry.registerSuperType("FixedNameService", "NameService", "com.example.NameService");
        registry.registerSuperType("FixedNameService", "Serializable", "java.io.Serializable");

        var supers = registry.getSuperTypes("FixedNameService");
        assertEquals(2, supers.size());
        assertTrue(supers.stream().anyMatch(s -> "NameService".equals(s.simpleName())));
        assertTrue(supers.stream().anyMatch(s -> "java.io.Serializable".equals(s.fqn())));
    }

    @Test
    void getSuperTypesReturnsEmptyForUnknownClass() {
        var registry = new GlobalDeclarationRegistry();
        assertTrue(registry.getSuperTypes("Unknown").isEmpty());
    }

    @Test
    void findImplementationsReturnsAllConcreteClasses() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("NameService", "getName", List.of(), "NameService.java"));
        registry.register(new DeclarationInfo("FixedNameService", "getName", List.of(), "FixedNameService.java"));
        registry.register(new DeclarationInfo("RandomNameService", "getName", List.of(), "RandomNameService.java"));
        registry.registerSuperType("FixedNameService", "NameService", "com.example.NameService");
        registry.registerSuperType("RandomNameService", "NameService", "com.example.NameService");

        var impls = registry.findImplementations("NameService", "getName", 0);

        assertEquals(2, impls.size());
        assertTrue(impls.stream().anyMatch(d -> "FixedNameService".equals(d.className())));
        assertTrue(impls.stream().anyMatch(d -> "RandomNameService".equals(d.className())));
    }

    @Test
    void findImplementationsFiltersByMethodAndParamCount() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("NameService", "getName", List.of(), "NameService.java"));
        registry.register(new DeclarationInfo("NameService", "getName", List.of("String"), "NameService.java"));
        registry.register(new DeclarationInfo("FixedNameService", "getName", List.of(), "FixedNameService.java"));
        registry.register(new DeclarationInfo("FixedNameService", "getName", List.of("String"), "FixedNameService.java"));
        registry.registerSuperType("FixedNameService", "NameService", "com.example.NameService");

        var zeroParam = registry.findImplementations("NameService", "getName", 0);
        assertEquals(1, zeroParam.size());
        assertEquals("FixedNameService", zeroParam.getFirst().className());

        var oneParam = registry.findImplementations("NameService", "getName", 1);
        assertEquals(1, oneParam.size());
    }

    @Test
    void findImplementationsReturnsEmptyForNoImplementations() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("NameService", "getName", List.of(), "NameService.java"));
        registry.register(new DeclarationInfo("UnrelatedService", "doStuff", List.of(), "UnrelatedService.java"));

        var impls = registry.findImplementations("NameService", "getName", 0);

        assertTrue(impls.isEmpty());
    }

    @Test
    void findImplementationsReturnsEmptyForNoSupertypes() {
        var registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("NameService", "getName", List.of(), "NameService.java"));

        var impls = registry.findImplementations("NameService", "getName", 0);

        assertTrue(impls.isEmpty());
    }
}
