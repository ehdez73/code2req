package com.github.ehdez73.code2req.synthesis.domain;

import com.github.ehdez73.code2req.synthesis.MethodIdentifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainRecordsTest {

    @Test
    void entryPointTypeHasAllValues() {
        assertEquals(6, EntryPointType.values().length);
        assertNotNull(EntryPointType.valueOf("HTTP"));
        assertNotNull(EntryPointType.valueOf("SCHEDULED"));
        assertNotNull(EntryPointType.valueOf("KAFKA"));
        assertNotNull(EntryPointType.valueOf("RABBITMQ"));
        assertNotNull(EntryPointType.valueOf("ACTIVEMQ"));
        assertNotNull(EntryPointType.valueOf("EVENT_LISTENER"));
    }

    @Test
    void entryPointConstructsCorrectly() {
        var ep = new EntryPoint(EntryPointType.HTTP, "Controller", "handle",
            "/src/Controller.java", "GET /api", 0.75);

        assertEquals(EntryPointType.HTTP, ep.type());
        assertEquals("Controller", ep.className());
        assertEquals("handle", ep.methodName());
        assertEquals("/src/Controller.java", ep.filePath());
        assertEquals("GET /api", ep.identifier());
        assertEquals(0.75, ep.priorityScore());
    }

    @Test
    void entryPointSupportsNullMethodName() {
        var ep = new EntryPoint(EntryPointType.HTTP, "Controller", null,
            "/src/Controller.java", "GET /api", 0.0);

        assertNull(ep.methodName());
    }

    @Test
    void entryPointEquality() {
        var ep1 = new EntryPoint(EntryPointType.HTTP, "Ctrl", "m", "/f.java", "GET /api", 0.5);
        var ep2 = new EntryPoint(EntryPointType.HTTP, "Ctrl", "m", "/f.java", "GET /api", 0.5);
        var ep3 = new EntryPoint(EntryPointType.SCHEDULED, "Ctrl", "m", "/f.java", "GET /api", 0.5);

        assertEquals(ep1, ep2);
        assertNotEquals(ep1, ep3);
    }

    @Test
    void methodIdentifierConstructsCorrectly() {
        var mi = new MethodIdentifier("OrderService", "placeOrder", "/src/OrderService.java");

        assertEquals("OrderService", mi.className());
        assertEquals("placeOrder", mi.methodName());
        assertEquals("/src/OrderService.java", mi.filePath());
    }

    @Test
    void methodIdentifierEquality() {
        var m1 = new MethodIdentifier("Svc", "doIt", "/f.java");
        var m2 = new MethodIdentifier("Svc", "doIt", "/f.java");
        var m3 = new MethodIdentifier("Svc", "doOther", "/f.java");

        assertEquals(m1, m2);
        assertNotEquals(m1, m3);
    }
}
