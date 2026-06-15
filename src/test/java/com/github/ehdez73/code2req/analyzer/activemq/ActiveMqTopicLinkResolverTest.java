package com.github.ehdez73.code2req.analyzer.activemq;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqPublisherInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActiveMqTopicLinkResolverTest {

    private final ActiveMqTopicLinkResolver resolver = new ActiveMqTopicLinkResolver();

    @Test
    void publisherAndListenerShareSameDestination() {
        var producer = new ActiveMqPublisherInfo("order.queue", "sendOrder", "OrderJmsService", "/app/OrderJmsService.java");
        var consumer = new ActiveMqInfo("order.queue", "handleJmsOrder", "OrderJmsListener", "/app/OrderJmsListener.java");
        var results = List.of(
            new AnalysisResult("/app/OrderJmsService.java", List.of(producer)),
            new AnalysisResult("/app/OrderJmsListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        var resolved = links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).toList();
        assertEquals(1, resolved.size());
        assertEquals("ACTIVEMQ", resolved.get(0).brokerType());
        assertEquals("order.queue", resolved.get(0).topic());
        assertEquals("OrderJmsService", resolved.get(0).producerClassName());
        assertEquals("OrderJmsListener", resolved.get(0).consumerClassName());
    }

    @Test
    void orphanProducerWithoutMatchingConsumer() {
        var producer = new ActiveMqPublisherInfo("unmatched.queue", "sendToUnmatched", "OrphanService", "/app/OrphanService.java");
        var results = List.of(
            new AnalysisResult("/app/OrphanService.java", List.of(producer))
        );

        var links = resolver.resolve(results);

        assertEquals(1, links.size());
        assertEquals("PENDING", links.get(0).resolvedStatus());
        assertEquals("unmatched.queue", links.get(0).topic());
        assertEquals("OrphanService", links.get(0).producerClassName());
        assertNull(links.get(0).consumerClassName());
    }

    @Test
    void orphanConsumerWithoutMatchingProducer() {
        var consumer = new ActiveMqInfo("standalone.dest", "handleStandalone", "StandaloneListener", "/app/StandaloneListener.java");
        var results = List.of(
            new AnalysisResult("/app/StandaloneListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        assertEquals(1, links.size());
        assertEquals("PENDING", links.get(0).resolvedStatus());
        assertEquals("standalone.dest", links.get(0).topic());
        assertEquals("StandaloneListener", links.get(0).consumerClassName());
        assertNull(links.get(0).producerClassName());
    }

    @Test
    void emptyFindings() {
        assertTrue(resolver.resolve(List.of()).isEmpty());
    }
}
