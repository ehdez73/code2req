package com.github.ehdez73.code2req.analyzer.event.broker.activemq;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActiveMqVisitorTest {

    private final ActiveMqVisitor visitor = new ActiveMqVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void extractsJmsListenerWithSingleDestination() {
        AnalysisResult result = analyze("OrderConsumer.java", """
            import org.springframework.jms.annotation.JmsListener;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderConsumer {
                @JmsListener(destination = "order.queue")
                public void onOrderEvent(String message) {}
            }
            """);

        assertEquals(1, result.findings(ActiveMqInfo.class).size());
        ActiveMqInfo ai = result.findings(ActiveMqInfo.class).getFirst();
        assertEquals("order.queue", ai.destination());
        assertEquals("onOrderEvent", ai.methodName());
        assertEquals("OrderConsumer", ai.className());
        assertEquals("OrderConsumer.java", ai.filePath());
    }

    @Test
    void extractsJmsListenerWithMultipleDestinations() {
        AnalysisResult result = analyze("MultiDestinationConsumer.java", """
            import org.springframework.jms.annotation.JmsListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiDestinationConsumer {
                @JmsListener(destination = "order.queue")
                public void onOrderEvent(String message) {}
                @JmsListener(destination = "notification.queue")
                public void onNotification(String message) {}
            }
            """);

        assertEquals(2, result.findings(ActiveMqInfo.class).size());
        assertEquals("onOrderEvent", result.findings(ActiveMqInfo.class).get(0).methodName());
        assertEquals("onNotification", result.findings(ActiveMqInfo.class).get(1).methodName());
    }

    @Test
    void detectsConvertAndSendOutbound() {
        AnalysisResult result = analyze("OrderPublisher.java", """
            import org.springframework.jms.core.JmsTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderPublisher {
                private final JmsTemplate jmsTemplate;
                public OrderPublisher(JmsTemplate jmsTemplate) {
                    this.jmsTemplate = jmsTemplate;
                }
                public void publishOrder() {
                    jmsTemplate.convertAndSend("order.queue", "order-data");
                }
            }
            """);

        assertEquals(1, result.findings(ActiveMqPublisherInfo.class).size());
        ActiveMqPublisherInfo ap = result.findings(ActiveMqPublisherInfo.class).getFirst();
        assertEquals("order.queue", ap.destination());
        assertEquals("publishOrder", ap.methodName());
        assertEquals("OrderPublisher", ap.className());
    }

    @Test
    void noJmsAnnotations_producesEmpty() {
        AnalysisResult result = analyze("PlainComponent.java", """
            import org.springframework.stereotype.Component;
            @Component
            public class PlainComponent {
                public void doSomething() {}
            }
            """);

        assertTrue(result.findings(ActiveMqInfo.class).isEmpty());
        assertTrue(result.findings(ActiveMqPublisherInfo.class).isEmpty());
    }

    @Test
    void multipleJmsListenerMethods_allCaptured() {
        AnalysisResult result = analyze("MultiListener.java", """
            import org.springframework.jms.annotation.JmsListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiListener {
                @JmsListener(destination = "orders")
                public void handleOrders(String msg) {}
                @JmsListener(destination = "payments")
                public void handlePayments(String msg) {}
            }
            """);

        assertEquals(2, result.findings(ActiveMqInfo.class).size());
        assertEquals("handleOrders", result.findings(ActiveMqInfo.class).get(0).methodName());
        assertEquals("handlePayments", result.findings(ActiveMqInfo.class).get(1).methodName());
    }

    @Test
    void jmsListenerWithMissingDestination_hasEmptyString() {
        AnalysisResult result = analyze("MinimalListener.java", """
            import org.springframework.jms.annotation.JmsListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MinimalListener {
                @JmsListener
                public void onMessage(String msg) {}
            }
            """);

        assertEquals(1, result.findings(ActiveMqInfo.class).size());
        assertEquals("", result.findings(ActiveMqInfo.class).getFirst().destination());
    }

    @Test
    void jmsTemplatePublishWithVariable_capturedAsVariableName() {
        AnalysisResult result = analyze("DynamicPublisher.java", """
            import org.springframework.jms.core.JmsTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class DynamicPublisher {
                private final JmsTemplate jmsTemplate;
                public DynamicPublisher(JmsTemplate jmsTemplate) {
                    this.jmsTemplate = jmsTemplate;
                }
                public void publish(String destination) {
                    jmsTemplate.convertAndSend(destination, "data");
                }
            }
            """);

        assertEquals(1, result.findings(ActiveMqPublisherInfo.class).size());
        ActiveMqPublisherInfo ap = result.findings(ActiveMqPublisherInfo.class).getFirst();
        assertEquals("destination", ap.destination());
        assertEquals("publish", ap.methodName());
    }

    @Test
    void jmsImportsButNoJmsUsage_producesEmpty() {
        AnalysisResult result = analyze("JmsAwareComponent.java", """
            import org.springframework.jms.annotation.JmsListener;
            import org.springframework.jms.core.JmsTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class JmsAwareComponent {
                public void doSomething() {}
            }
            """);

        assertTrue(result.findings(ActiveMqInfo.class).isEmpty());
        assertTrue(result.findings(ActiveMqPublisherInfo.class).isEmpty());
    }
}
