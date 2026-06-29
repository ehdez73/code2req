package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka.KafkaPublisherInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KafkaTopicLinkResolverTest {

    private final KafkaTopicLinkResolver resolver = new KafkaTopicLinkResolver();

    @Test
    void publisherAndListenerShareSameTopic() {
        var producer = new KafkaPublisherInfo("order-events", "sendOrder", "OrderService", "/app/OrderService.java");
        var consumer = new KafkaInfo("order-events", "handleOrder", "OrderHandler", "/app/OrderHandler.java", false);
        var results = List.of(
            new AnalysisResult("/app/OrderService.java", List.of(producer)),
            new AnalysisResult("/app/OrderHandler.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        assertEquals(1, links.size());
        assertEquals("KAFKA", links.get(0).brokerType());
        assertEquals("RESOLVED", links.get(0).resolvedStatus());
        assertEquals("order-events", links.get(0).topic());
        assertEquals("OrderService", links.get(0).producerClassName());
        assertEquals("OrderHandler", links.get(0).consumerClassName());
    }

    @Test
    void topicPatternIsSkippedForMatching() {
        var producer = new KafkaPublisherInfo("order-events", "sendOrder", "OrderService", "/app/OrderService.java");
        var patternConsumer = new KafkaInfo("order.*", "handleAny", "PatternHandler", "/app/PatternHandler.java", true);
        var results = List.of(
            new AnalysisResult("/app/OrderService.java", List.of(producer)),
            new AnalysisResult("/app/PatternHandler.java", List.of(patternConsumer))
        );

        var links = resolver.resolve(results);

        var resolved = links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).toList();
        assertEquals(0, resolved.size());
        assertTrue(links.stream().anyMatch(l -> l.producerClassName() != null), "Producer should be orphan");
        assertTrue(links.stream().anyMatch(l -> l.consumerClassName() != null), "Pattern consumer should be orphan");
    }

    @Test
    void orphanConsumerWithoutMatchingProducer() {
        var consumer = new KafkaInfo("standalone-topic", "listenStandalone", "StandaloneListener", "/app/StandaloneListener.java", false);
        var results = List.of(
            new AnalysisResult("/app/StandaloneListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        assertEquals(1, links.size());
        assertEquals("PENDING", links.get(0).resolvedStatus());
        assertEquals("standalone-topic", links.get(0).topic());
        assertEquals("StandaloneListener", links.get(0).consumerClassName());
        assertNull(links.get(0).producerClassName());
    }

    @Test
    void multipleTopicsOnListener() {
        var producer1 = new KafkaPublisherInfo("topic-a", "sendA", "PublisherA", "/app/PublisherA.java");
        var producer2 = new KafkaPublisherInfo("topic-b", "sendB", "PublisherB", "/app/PublisherB.java");
        var consumer = new KafkaInfo("topic-a, topic-b", "handleBoth", "MultiTopicListener", "/app/MultiTopicListener.java", false);
        var results = List.of(
            new AnalysisResult("/app/PublisherA.java", List.of(producer1)),
            new AnalysisResult("/app/PublisherB.java", List.of(producer2)),
            new AnalysisResult("/app/MultiTopicListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        var resolved = links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).toList();
        assertEquals(2, resolved.size());
        assertTrue(resolved.stream().anyMatch(l -> "topic-a".equals(l.topic())));
        assertTrue(resolved.stream().anyMatch(l -> "topic-b".equals(l.topic())));
    }

    @Test
    void listenerOnMultipleQueuesEachProducerCreatesLink() {
        var producer = new KafkaPublisherInfo("topic-a", "sendA", "Publisher", "/app/Publisher.java");
        var consumer = new KafkaInfo("topic-a, topic-b", "handleBoth", "MultiTopicListener", "/app/MultiTopicListener.java", false);
        var results = List.of(
            new AnalysisResult("/app/Publisher.java", List.of(producer)),
            new AnalysisResult("/app/MultiTopicListener.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        var resolved = links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).toList();
        assertEquals(1, resolved.size());
        assertEquals("topic-a", resolved.get(0).topic());
    }

    @Test
    void emptyFindings() {
        assertTrue(resolver.resolve(List.of()).isEmpty());
    }
}
