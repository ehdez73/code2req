package com.github.ehdez73.code2req.extraction.domain.model;

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
    void httpEntryPointConstructsCorrectly() {
        var ep = new HttpEntryPoint("GET /api", "Controller", "handle", "/src/Controller.java",
            0.75, false, "GET", "/api", List.of("id"), List.of("CreateUserRequest"));

        assertEquals("GET /api", ep.id());
        assertEquals(EntryPointType.HTTP, ep.type());
        assertEquals("GET", ep.httpMethod());
        assertEquals("/api", ep.path());
        assertEquals("Controller", ep.className());
        assertEquals("handle", ep.methodName());
        assertEquals("/src/Controller.java", ep.filePath());
        assertEquals(0.75, ep.priorityScore());
        assertFalse(ep.trivial());
        assertEquals(List.of("id"), ep.pathVariables());
        assertEquals(List.of("CreateUserRequest"), ep.requestBodies());
    }

    @Test
    void httpEntryPointSupportsNullMethodName() {
        var ep = new HttpEntryPoint("GET /api", "Controller", null, "/src/Controller.java",
            0.0, false, "GET", "/api", List.of(), List.of());

        assertNull(ep.methodName());
    }

    @Test
    void entryPointEquality() {
        var ep1 = new HttpEntryPoint("id1", "Ctrl", "m", "/f.java",
            0.5, false, "GET", "/api", List.of(), List.of());
        var ep2 = new HttpEntryPoint("id1", "Ctrl", "m", "/f.java",
            0.5, false, "GET", "/api", List.of(), List.of());
        var ep3 = new ScheduledEntryPoint("id2", "Ctrl", "m", "/f.java",
            0.5, false, "0 0 * * *");

        assertEquals(ep1, ep2);
        assertNotEquals(ep1, ep3);
    }

    @Test
    void sealedInterfacePermitsAllSixTypes() {
        EntryPoint ep = new HttpEntryPoint("id", "C", null, "/f.java", 0, false, "GET", "/", List.of(), List.of());
        assertInstanceOf(HttpEntryPoint.class, ep);

        ep = new ScheduledEntryPoint("id", "C", "m", "/f.java", 0, false, "0 * * * *");
        assertInstanceOf(ScheduledEntryPoint.class, ep);

        ep = new KafkaEntryPoint("events", "C", "m", "/f.java", 0, false, "events", false, "OrderCreated");
        assertInstanceOf(KafkaEntryPoint.class, ep);

        ep = new RabbitMqEntryPoint("q", "C", "m", "/f.java", 0, false, "q", "PaymentProcessed");
        assertInstanceOf(RabbitMqEntryPoint.class, ep);

        ep = new ActiveMqEntryPoint("d", "C", "m", "/f.java", 0, false, "d", "InventoryUpdated");
        assertInstanceOf(ActiveMqEntryPoint.class, ep);

        ep = new EventListenerEntryPoint("MyEvent", "C", "m", "/f.java", 0, false, "MyEvent");
        assertInstanceOf(EventListenerEntryPoint.class, ep);
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
