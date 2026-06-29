package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.extraction.domain.model.MethodIdentifier;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        var ep = new EntryPoint("GET /api", EntryPointType.HTTP, "GET", "/api",
            "Controller", "handle", "/src/Controller.java", 0.75,
            false, List.of(), null, null);

        assertEquals("GET /api", ep.id());
        assertEquals(EntryPointType.HTTP, ep.type());
        assertEquals("GET", ep.httpMethod());
        assertEquals("/api", ep.path());
        assertEquals("Controller", ep.className());
        assertEquals("handle", ep.methodName());
        assertEquals("/src/Controller.java", ep.filePath());
        assertEquals(0.75, ep.priorityScore());
        assertFalse(ep.trivial());
        assertTrue(ep.pathVariables().isEmpty());
        assertNull(ep.schedule());
        assertNull(ep.topicOrQueue());
    }

    @Test
    void entryPointSupportsNullMethodName() {
        var ep = new EntryPoint("GET /api", EntryPointType.HTTP, "GET", "/api",
            "Controller", null, "/src/Controller.java", 0.0,
            false, List.of(), null, null);

        assertNull(ep.methodName());
    }

    @Test
    void entryPointEquality() {
        var ep1 = new EntryPoint("id1", EntryPointType.HTTP, "GET", "/api",
            "Ctrl", "m", "/f.java", 0.5, false, List.of(), null, null);
        var ep2 = new EntryPoint("id1", EntryPointType.HTTP, "GET", "/api",
            "Ctrl", "m", "/f.java", 0.5, false, List.of(), null, null);
        var ep3 = new EntryPoint("id2", EntryPointType.SCHEDULED, null, null,
            "Ctrl", "m", "/f.java", 0.5, false, List.of(), "0 0 * * *", null);

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
