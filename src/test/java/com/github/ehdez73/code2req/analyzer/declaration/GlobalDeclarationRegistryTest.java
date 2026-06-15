package com.github.ehdez73.code2req.analyzer.declaration;

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
}
