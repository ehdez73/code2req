package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaVisitorTest {

    private final KafkaVisitor visitor = new KafkaVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void extractsKafkaListenerWithSingleTopic() {
        AnalysisResult result = analyze("OrderConsumer.java", """
            import org.springframework.kafka.annotation.KafkaListener;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderConsumer {
                @KafkaListener(topics = "order-events")
                public void onOrderEvent(String message) {}
            }
            """);

        assertEquals(1, result.findings(KafkaInfo.class).size());
        KafkaInfo ki = result.findings(KafkaInfo.class).getFirst();
        assertEquals("order-events", ki.topics());
        assertEquals("onOrderEvent", ki.methodName());
        assertEquals("OrderConsumer", ki.className());
        assertEquals("OrderConsumer.java", ki.filePath());
        assertFalse(ki.isPattern());
    }

    @Test
    void extractsKafkaListenerWithMultipleTopics() {
        AnalysisResult result = analyze("MultiTopicConsumer.java", """
            import org.springframework.kafka.annotation.KafkaListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiTopicConsumer {
                @KafkaListener(topics = {"order-events", "inventory-events"})
                public void onEvent(String message) {}
            }
            """);

        assertEquals(1, result.findings(KafkaInfo.class).size());
        KafkaInfo ki = result.findings(KafkaInfo.class).getFirst();
        assertTrue(ki.topics().contains("order-events"));
        assertTrue(ki.topics().contains("inventory-events"));
        assertEquals("onEvent", ki.methodName());
        assertFalse(ki.isPattern());
    }

    @Test
    void extractsKafkaListenerWithTopicPattern() {
        AnalysisResult result = analyze("PatternConsumer.java", """
            import org.springframework.kafka.annotation.KafkaListener;
            import org.springframework.stereotype.Component;
            @Component
            public class PatternConsumer {
                @KafkaListener(topicPattern = "orders-.*")
                public void onOrderEvent(String message) {}
            }
            """);

        assertEquals(1, result.findings(KafkaInfo.class).size());
        KafkaInfo ki = result.findings(KafkaInfo.class).getFirst();
        assertEquals("orders-.*", ki.topics());
        assertTrue(ki.isPattern());
        assertEquals("PatternConsumer", ki.className());
    }

    @Test
    void detectsKafkaTemplateSendOutbound() {
        AnalysisResult result = analyze("OrderPublisher.java", """
            import org.springframework.kafka.core.KafkaTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class OrderPublisher {
                private final KafkaTemplate<String, String> kafkaTemplate;
                public OrderPublisher(KafkaTemplate<String, String> kafkaTemplate) {
                    this.kafkaTemplate = kafkaTemplate;
                }
                public void publishOrder() {
                    kafkaTemplate.send("order-events", "order-data");
                }
            }
            """);

        assertEquals(1, result.findings(KafkaPublisherInfo.class).size());
        KafkaPublisherInfo kp = result.findings(KafkaPublisherInfo.class).getFirst();
        assertEquals("order-events", kp.topic());
        assertEquals("publishOrder", kp.methodName());
        assertEquals("OrderPublisher", kp.className());
    }

    @Test
    void noKafkaAnnotations_producesEmpty() {
        AnalysisResult result = analyze("PlainComponent.java", """
            import org.springframework.stereotype.Component;
            @Component
            public class PlainComponent {
                public void doSomething() {}
            }
            """);

        assertTrue(result.findings(KafkaInfo.class).isEmpty());
        assertTrue(result.findings(KafkaPublisherInfo.class).isEmpty());
    }

    @Test
    void multipleKafkaListenerMethods_allCaptured() {
        AnalysisResult result = analyze("MultiListener.java", """
            import org.springframework.kafka.annotation.KafkaListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MultiListener {
                @KafkaListener(topics = "orders")
                public void handleOrders(String msg) {}
                @KafkaListener(topics = "payments")
                public void handlePayments(String msg) {}
            }
            """);

        assertEquals(2, result.findings(KafkaInfo.class).size());
        assertEquals("handleOrders", result.findings(KafkaInfo.class).get(0).methodName());
        assertEquals("handlePayments", result.findings(KafkaInfo.class).get(1).methodName());
    }

    @Test
    void kafkaListenerWithMissingTopics_hasEmptyString() {
        AnalysisResult result = analyze("MinimalListener.java", """
            import org.springframework.kafka.annotation.KafkaListener;
            import org.springframework.stereotype.Component;
            @Component
            public class MinimalListener {
                @KafkaListener
                public void onMessage(String msg) {}
            }
            """);

        assertEquals(1, result.findings(KafkaInfo.class).size());
        assertEquals("", result.findings(KafkaInfo.class).getFirst().topics());
    }

    @Test
    void kafkaTemplateSendWithVariable_topicCapturedAsVariableName() {
        AnalysisResult result = analyze("DynamicPublisher.java", """
            import org.springframework.kafka.core.KafkaTemplate;
            import org.springframework.stereotype.Component;
            @Component
            public class DynamicPublisher {
                private final KafkaTemplate<String, String> kafkaTemplate;
                public DynamicPublisher(KafkaTemplate<String, String> kafkaTemplate) {
                    this.kafkaTemplate = kafkaTemplate;
                }
                public void publish(String topic) {
                    kafkaTemplate.send(topic, "data");
                }
            }
            """);

        assertEquals(1, result.findings(KafkaPublisherInfo.class).size());
        KafkaPublisherInfo kp = result.findings(KafkaPublisherInfo.class).getFirst();
        assertEquals("topic", kp.topic());
        assertEquals("publish", kp.methodName());
    }
}
