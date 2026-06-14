package com.github.ehdez73.code2req.analyzer.rabbitmq;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMqVisitorTest {

    private final RabbitMqVisitor visitor = new RabbitMqVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, filePath);
        return builder.build(filePath);
    }

    @Test
    void extractsRabbitListenerWithSingleQueue() {
        AnalysisResult result = analyze("OrderConsumer.java", """
            import org.springframework.amqp.rabbit.annotation.RabbitListener;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderConsumer {
                @RabbitListener(queues = "order.queue")
                public void onOrderEvent(String message) {}
            }
            """);

        assertEquals(1, result.findings(RabbitMqInfo.class).size());
        RabbitMqInfo ri = result.findings(RabbitMqInfo.class).getFirst();
        assertEquals("order.queue", ri.queues());
        assertEquals("onOrderEvent", ri.methodName());
        assertEquals("OrderConsumer", ri.className());
        assertEquals("OrderConsumer.java", ri.filePath());
    }

    @Test
    void extractsRabbitListenerWithMultipleQueues() {
        AnalysisResult result = analyze("MultiQueueConsumer.java", """
            import org.springframework.amqp.rabbit.annotation.RabbitListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiQueueConsumer {
                @RabbitListener(queues = {"order.queue", "notification.queue"})
                public void onEvent(String message) {}
            }
            """);

        assertEquals(1, result.findings(RabbitMqInfo.class).size());
        RabbitMqInfo ri = result.findings(RabbitMqInfo.class).getFirst();
        assertTrue(ri.queues().contains("order.queue"));
        assertTrue(ri.queues().contains("notification.queue"));
        assertEquals("onEvent", ri.methodName());
    }

    @Test
    void detectsConvertAndSendOutbound() {
        AnalysisResult result = analyze("OrderPublisher.java", """
            import org.springframework.amqp.rabbit.core.RabbitTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderPublisher {
                private final RabbitTemplate rabbitTemplate;
                public OrderPublisher(RabbitTemplate rabbitTemplate) {
                    this.rabbitTemplate = rabbitTemplate;
                }
                public void publishOrder() {
                    rabbitTemplate.convertAndSend("order.exchange", "order.routing", "order-data");
                }
            }
            """);

        assertEquals(1, result.findings(RabbitMqPublisherInfo.class).size());
        RabbitMqPublisherInfo rp = result.findings(RabbitMqPublisherInfo.class).getFirst();
        assertEquals("order.exchange", rp.exchange());
        assertEquals("order.routing", rp.routingKey());
        assertEquals("publishOrder", rp.methodName());
        assertEquals("OrderPublisher", rp.className());
    }

    @Test
    void detectsSendOutbound() {
        AnalysisResult result = analyze("OrderSender.java", """
            import org.springframework.amqp.rabbit.core.RabbitTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderSender {
                private final RabbitTemplate rabbitTemplate;
                public OrderSender(RabbitTemplate rabbitTemplate) {
                    this.rabbitTemplate = rabbitTemplate;
                }
                public void sendOrder() {
                    rabbitTemplate.send("order.exchange", "order.routing", new org.springframework.amqp.core.Message("test".getBytes()));
                }
            }
            """);

        assertEquals(1, result.findings(RabbitMqPublisherInfo.class).size());
        RabbitMqPublisherInfo rp = result.findings(RabbitMqPublisherInfo.class).getFirst();
        assertEquals("order.exchange", rp.exchange());
        assertEquals("order.routing", rp.routingKey());
        assertEquals("sendOrder", rp.methodName());
    }

    @Test
    void noRabbitMqAnnotations_producesEmpty() {
        AnalysisResult result = analyze("PlainComponent.java", """
            import org.springframework.stereotype.Component;
            @Component
            public class PlainComponent {
                public void doSomething() {}
            }
            """);

        assertTrue(result.findings(RabbitMqInfo.class).isEmpty());
        assertTrue(result.findings(RabbitMqPublisherInfo.class).isEmpty());
    }

    @Test
    void multipleRabbitListenerMethods_allCaptured() {
        AnalysisResult result = analyze("MultiListener.java", """
            import org.springframework.amqp.rabbit.annotation.RabbitListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiListener {
                @RabbitListener(queues = "orders")
                public void handleOrders(String msg) {}
                @RabbitListener(queues = "payments")
                public void handlePayments(String msg) {}
            }
            """);

        assertEquals(2, result.findings(RabbitMqInfo.class).size());
        assertEquals("handleOrders", result.findings(RabbitMqInfo.class).get(0).methodName());
        assertEquals("handlePayments", result.findings(RabbitMqInfo.class).get(1).methodName());
    }

    @Test
    void rabbitListenerWithMissingQueues_hasEmptyString() {
        AnalysisResult result = analyze("MinimalListener.java", """
            import org.springframework.amqp.rabbit.annotation.RabbitListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MinimalListener {
                @RabbitListener
                public void onMessage(String msg) {}
            }
            """);

        assertEquals(1, result.findings(RabbitMqInfo.class).size());
        assertEquals("", result.findings(RabbitMqInfo.class).getFirst().queues());
    }

    @Test
    void rabbitTemplatePublishWithVariable_capturedAsVariableName() {
        AnalysisResult result = analyze("DynamicPublisher.java", """
            import org.springframework.amqp.rabbit.core.RabbitTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class DynamicPublisher {
                private final RabbitTemplate rabbitTemplate;
                public DynamicPublisher(RabbitTemplate rabbitTemplate) {
                    this.rabbitTemplate = rabbitTemplate;
                }
                public void publish(String exchange, String routingKey) {
                    rabbitTemplate.convertAndSend(exchange, routingKey, "data");
                }
            }
            """);

        assertEquals(1, result.findings(RabbitMqPublisherInfo.class).size());
        RabbitMqPublisherInfo rp = result.findings(RabbitMqPublisherInfo.class).getFirst();
        assertEquals("exchange", rp.exchange());
        assertEquals("routingKey", rp.routingKey());
        assertEquals("publish", rp.methodName());
    }
}
