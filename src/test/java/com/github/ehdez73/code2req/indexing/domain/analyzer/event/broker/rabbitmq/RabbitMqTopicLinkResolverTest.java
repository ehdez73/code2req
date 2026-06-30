package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq.RabbitMqPublisherInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMqTopicLinkResolverTest {

    private final RabbitMqTopicLinkResolver resolver = new RabbitMqTopicLinkResolver();

    @Test
    void publisherAndListenerShareSameQueue() {
        var producer = new RabbitMqPublisherInfo("order.exchange", "order.queue", "sendOrder", "OrderService", "/app/OrderService.java");
        var consumer = new RabbitMqInfo("order.queue", "handleOrder", "OrderMqListener", "/app/OrderMqListener.java", "");
        var results = List.of(
            new AnalysisResult("/app/OrderService.java", List.of(producer)),
            new AnalysisResult("/app/OrderMqListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        var resolved = links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).toList();
        assertEquals(1, resolved.size());
        assertEquals("RABBITMQ", resolved.get(0).brokerType());
        assertEquals("order.queue", resolved.get(0).topic());
        assertEquals("OrderService", resolved.get(0).producerClassName());
        assertEquals("OrderMqListener", resolved.get(0).consumerClassName());
    }

    @Test
    void publisherWithoutRoutingKeyIsOrphan() {
        var producer = new RabbitMqPublisherInfo("order.exchange", "", "sendOrder", "OrderService", "/app/OrderService.java");
        var results = List.of(
            new AnalysisResult("/app/OrderService.java", List.of(producer))
        );

        var links = resolver.resolve(results);

        assertEquals(0, links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).count());
        var pending = links.stream().filter(l -> "PENDING".equals(l.resolvedStatus())).toList();
        assertEquals(1, pending.size());
        assertEquals("OrderService", pending.get(0).producerClassName());
        assertNull(pending.get(0).consumerClassName());
    }

    @Test
    void crossTargetMatching() {
        var producer = new RabbitMqPublisherInfo("order.exchange", "order.queue", "sendOrder", "OrderService", "/target-a/src/OrderService.java");
        var consumer = new RabbitMqInfo("order.queue", "handleOrder", "OrderMqListener", "/target-b/src/OrderMqListener.java", "");
        var results = List.of(
            new AnalysisResult("/target-a/src/OrderService.java", List.of(producer)),
            new AnalysisResult("/target-b/src/OrderMqListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        var resolved = links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).toList();
        assertEquals(1, resolved.size());
        assertEquals("order.queue", resolved.get(0).topic());
        assertEquals("/target-a/src/OrderService.java", resolved.get(0).producerFilePath());
        assertEquals("/target-b/src/OrderMqListener.java", resolved.get(0).consumerFilePath());
    }

    @Test
    void orphanConsumerWithoutMatchingProducer() {
        var consumer = new RabbitMqInfo("standalone.queue", "handleStandalone", "StandaloneListener", "/app/StandaloneListener.java", "");
        var results = List.of(
            new AnalysisResult("/app/StandaloneListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        assertEquals(1, links.size());
        assertEquals("PENDING", links.get(0).resolvedStatus());
        assertEquals("standalone.queue", links.get(0).topic());
        assertEquals("StandaloneListener", links.get(0).consumerClassName());
        assertNull(links.get(0).producerClassName());
    }

    @Test
    void emptyFindings() {
        assertTrue(resolver.resolve(List.of()).isEmpty());
    }
}
