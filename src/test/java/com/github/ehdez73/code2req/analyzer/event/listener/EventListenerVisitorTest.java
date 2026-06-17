package com.github.ehdez73.code2req.analyzer.event.listener;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventListenerVisitorTest {

    private final EventListenerVisitor visitor = new EventListenerVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void extractsEventListenerWithExplicitEventType() {
        AnalysisResult result = analyze("MyListener.java", """
            import org.springframework.context.event.EventListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MyListener {
                @EventListener(UserCreatedEvent.class)
                public void handleUserCreated(Object event) {}
            }
            """);

        assertEquals(1, result.findings(EventListenerInfo.class).size());
        EventListenerInfo el = result.findings(EventListenerInfo.class).getFirst();
        assertEquals("UserCreatedEvent", el.eventType());
        assertEquals("handleUserCreated", el.methodName());
        assertEquals("MyListener", el.className());
        assertEquals("MyListener.java", el.filePath());
        assertTrue(el.callChain().isEmpty());
    }

    @Test
    void extractsEventListenerWithInferredEventType() {
        AnalysisResult result = analyze("OrderListener.java", """
            import org.springframework.context.event.EventListener;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderListener {
                @EventListener
                public void onOrderPlaced(OrderPlacedEvent event) {}
            }
            """);

        assertEquals(1, result.findings(EventListenerInfo.class).size());
        EventListenerInfo el = result.findings(EventListenerInfo.class).getFirst();
        assertEquals("OrderPlacedEvent", el.eventType());
        assertEquals("onOrderPlaced", el.methodName());
        assertEquals("OrderListener", el.className());
    }

    @Test
    void extractsEventListenerWithNormalAnnotationValue() {
        AnalysisResult result = analyze("PaymentListener.java", """
            import org.springframework.context.event.EventListener;
            import org.springframework.stereotype.Component;
            @Component
            public class PaymentListener {
                @EventListener(classes = PaymentReceivedEvent.class)
                public void onPayment(Object event) {}
            }
            """);

        assertEquals(1, result.findings(EventListenerInfo.class).size());
        assertEquals("PaymentReceivedEvent", result.findings(EventListenerInfo.class).getFirst().eventType());
    }

    @Test
    void extractsCallChainFromEventListener() {
        AnalysisResult result = analyze("ServiceListener.java", """
            import org.springframework.context.event.EventListener;
            import org.springframework.stereotype.Component;
            @Component
            public class ServiceListener {
                @EventListener
                public void handle(SomeEvent event) {
                    userService.createUser(event);
                    notificationService.notify(event);
                }
            }
            """);

        assertEquals(1, result.findings(EventListenerInfo.class).size());
        EventListenerInfo el = result.findings(EventListenerInfo.class).getFirst();
        assertEquals(2, el.callChain().size());

        MethodCallInfo call1 = el.callChain().get(0);
        assertEquals("userService", call1.targetType());
        assertEquals("createUser", call1.methodName());
        assertEquals(1, call1.depth());

        MethodCallInfo call2 = el.callChain().get(1);
        assertEquals("notificationService", call2.targetType());
        assertEquals("notify", call2.methodName());
        assertEquals(1, call2.depth());
    }

    @Test
    void extractsNestedCallChainUpToDepth3() {
        AnalysisResult result = analyze("NestedListener.java", """
            import org.springframework.context.event.EventListener;
            import org.springframework.stereotype.Component;
            @Component
            public class NestedListener {
                @EventListener
                public void handle(SomeEvent event) {
                    service.doSomething(helper.getData(object.getProp()));
                }
            }
            """);

        assertEquals(1, result.findings(EventListenerInfo.class).size());
        EventListenerInfo el = result.findings(EventListenerInfo.class).getFirst();

        assertFalse(el.callChain().isEmpty());
        MethodCallInfo depth1 = el.callChain().get(0);
        assertEquals("service", depth1.targetType());
        assertEquals("doSomething", depth1.methodName());
        assertEquals(1, depth1.depth());

        boolean hasDepth2 = el.callChain().stream().anyMatch(c -> c.depth() == 2);
        assertTrue(hasDepth2, "Expected a depth-2 call");

        boolean hasDepth3 = el.callChain().stream().anyMatch(c -> c.depth() == 3);
        assertTrue(hasDepth3, "Expected a depth-3 call");
    }

    @Test
    void noEventListenerAnnotation_producesEmpty() {
        AnalysisResult result = analyze("PlainComponent.java", """
            import org.springframework.stereotype.Component;
            @Component
            public class PlainComponent {
                public void doSomething() {}
            }
            """);

        assertTrue(result.findings(EventListenerInfo.class).isEmpty());
        assertTrue(result.findings(EventPublisherInfo.class).isEmpty());
    }

    @Test
    void multipleEventListenerMethods_allCaptured() {
        AnalysisResult result = analyze("MultiListener.java", """
            import org.springframework.context.event.EventListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiListener {
                @EventListener
                public void handleFirst(FirstEvent e) {}
                @EventListener
                public void handleSecond(SecondEvent e) {}
            }
            """);

        assertEquals(2, result.findings(EventListenerInfo.class).size());
        assertEquals("handleFirst", result.findings(EventListenerInfo.class).get(0).methodName());
        assertEquals("handleSecond", result.findings(EventListenerInfo.class).get(1).methodName());
    }

    @Test
    void detectsApplicationEventPublisherUsage() {
        AnalysisResult result = analyze("EventPublisherService.java", """
            import org.springframework.context.ApplicationEventPublisher;
            import org.springframework.stereotype.Component;
            @Component
            public class EventPublisherService {
                private final ApplicationEventPublisher publisher;
                public EventPublisherService(ApplicationEventPublisher publisher) {
                    this.publisher = publisher;
                }
                public void createUser() {
                    publisher.publishEvent(new UserCreatedEvent());
                }
            }
            """);

        assertEquals(1, result.findings(EventPublisherInfo.class).size());
        EventPublisherInfo ep = result.findings(EventPublisherInfo.class).getFirst();
        assertEquals("EventPublisherService", ep.className());
        assertEquals("UserCreatedEvent", ep.eventType());
    }

    @Test
    void detectsMultiplePublishEvents() {
        AnalysisResult result = analyze("MultiPublisher.java", """
            import org.springframework.context.ApplicationEventPublisher;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiPublisher {
                private final ApplicationEventPublisher publisher;
                public MultiPublisher(ApplicationEventPublisher publisher) {
                    this.publisher = publisher;
                }
                public void doStuff() {
                    publisher.publishEvent(new EventA());
                    publisher.publishEvent(new EventB());
                }
            }
            """);

        assertEquals(2, result.findings(EventPublisherInfo.class).size());
        assertEquals("EventA", result.findings(EventPublisherInfo.class).get(0).eventType());
        assertEquals("EventB", result.findings(EventPublisherInfo.class).get(1).eventType());
    }

    @Test
    void listenerWithEmptyBody_hasEmptyCallChain() {
        AnalysisResult result = analyze("EmptyListener.java", """
            import org.springframework.context.event.EventListener;
            import org.springframework.stereotype.Component;
            @Component
            public class EmptyListener {
                @EventListener
                public void handle(SomeEvent event) {}
            }
            """);

        assertEquals(1, result.findings(EventListenerInfo.class).size());
        assertTrue(result.findings(EventListenerInfo.class).getFirst().callChain().isEmpty());
    }
}
