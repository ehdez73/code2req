package com.github.ehdez73.code2req.analyzer.eventlink;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqInfo;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.activemq.ActiveMqTopicLinkResolver;
import com.github.ehdez73.code2req.analyzer.kafka.KafkaInfo;
import com.github.ehdez73.code2req.analyzer.kafka.KafkaPublisherInfo;
import com.github.ehdez73.code2req.analyzer.kafka.KafkaTopicLinkResolver;
import com.github.ehdez73.code2req.analyzer.rabbitmq.RabbitMqInfo;
import com.github.ehdez73.code2req.analyzer.rabbitmq.RabbitMqPublisherInfo;
import com.github.ehdez73.code2req.analyzer.rabbitmq.RabbitMqTopicLinkResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopicLinkResolverTest {

    @Test
    void delegatesToAllStrategies() {
        var resolver = new TopicLinkResolver(List.of(
            new KafkaTopicLinkResolver(),
            new RabbitMqTopicLinkResolver(),
            new ActiveMqTopicLinkResolver()
        ));

        var kafkaProducer = new KafkaPublisherInfo("events", "sendEvent", "KafkaService", "/app/KafkaService.java");
        var kafkaConsumer = new KafkaInfo("events", "handleEvent", "KafkaHandler", "/app/KafkaHandler.java", false);
        var rabbitProducer = new RabbitMqPublisherInfo("ex", "q1", "sendQ", "RabbitService", "/app/RabbitService.java");
        var rabbitConsumer = new RabbitMqInfo("q1", "handleQ", "RabbitHandler", "/app/RabbitHandler.java");
        var activeProducer = new ActiveMqPublisherInfo("dest", "sendDest", "ActiveService", "/app/ActiveService.java");
        var activeConsumer = new ActiveMqInfo("dest", "handleDest", "ActiveHandler", "/app/ActiveHandler.java");

        var results = List.of(
            new AnalysisResult("/app/KafkaService.java", List.of(kafkaProducer)),
            new AnalysisResult("/app/KafkaHandler.java", List.of(kafkaConsumer)),
            new AnalysisResult("/app/RabbitService.java", List.of(rabbitProducer)),
            new AnalysisResult("/app/RabbitHandler.java", List.of(rabbitConsumer)),
            new AnalysisResult("/app/ActiveService.java", List.of(activeProducer)),
            new AnalysisResult("/app/ActiveHandler.java", List.of(activeConsumer))
        );

        var links = resolver.resolve(results);

        var resolved = links.stream().filter(l -> "RESOLVED".equals(l.resolvedStatus())).toList();
        assertEquals(3, resolved.size());
        assertTrue(resolved.stream().anyMatch(l -> "KAFKA".equals(l.brokerType())));
        assertTrue(resolved.stream().anyMatch(l -> "RABBITMQ".equals(l.brokerType())));
        assertTrue(resolved.stream().anyMatch(l -> "ACTIVEMQ".equals(l.brokerType())));
    }

    @Test
    void emptyStrategiesReturnsEmpty() {
        var resolver = new TopicLinkResolver(List.of());
        assertTrue(resolver.resolve(List.of()).isEmpty());
    }

    @Test
    void singleStrategyOnlyReturnsItsLinks() {
        var resolver = new TopicLinkResolver(List.of(new KafkaTopicLinkResolver()));

        var producer = new KafkaPublisherInfo("events", "sendEvent", "KafkaService", "/app/KafkaService.java");
        var consumer = new KafkaInfo("events", "handleEvent", "KafkaHandler", "/app/KafkaHandler.java", false);
        var results = List.of(
            new AnalysisResult("/app/KafkaService.java", List.of(producer)),
            new AnalysisResult("/app/KafkaHandler.java", List.of(consumer))
        );

        var links = resolver.resolve(results);

        assertEquals(1, links.size());
        assertEquals("KAFKA", links.get(0).brokerType());
    }
}
