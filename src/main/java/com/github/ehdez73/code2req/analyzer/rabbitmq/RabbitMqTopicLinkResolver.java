package com.github.ehdez73.code2req.analyzer.rabbitmq;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.eventlink.TopicLink;
import com.github.ehdez73.code2req.analyzer.eventlink.TopicLinkResolverStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RabbitMqTopicLinkResolver implements TopicLinkResolverStrategy {

    @Override
    public String brokerType() {
        return "RABBITMQ";
    }

    @Override
    public List<TopicLink> resolve(List<AnalysisResult> allResults) {
        List<RabbitMqPublisherInfo> producers = new ArrayList<>();
        List<RabbitMqInfo> consumers = new ArrayList<>();
        for (AnalysisResult r : allResults) {
            producers.addAll(r.findings(RabbitMqPublisherInfo.class));
            consumers.addAll(r.findings(RabbitMqInfo.class));
        }
        return resolve(producers, consumers);
    }

    List<TopicLink> resolve(List<RabbitMqPublisherInfo> producers, List<RabbitMqInfo> consumers) {
        List<TopicLink> links = new ArrayList<>();

        Map<String, List<RabbitMqInfo>> consumerByQueue = new HashMap<>();
        for (RabbitMqInfo c : consumers) {
            for (String queue : splitTopics(c.queues())) {
                consumerByQueue.computeIfAbsent(queue.trim(), k -> new ArrayList<>()).add(c);
            }
        }

        Set<RabbitMqInfo> matchedConsumers = new HashSet<>();
        for (RabbitMqPublisherInfo p : producers) {
            String routingKey = p.routingKey();
            if (routingKey == null || routingKey.isBlank()) {
                links.add(TopicLink.orphanProducer(brokerType(), p.exchange(), p.className(), p.filePath()));
                continue;
            }
            List<RabbitMqInfo> matching = consumerByQueue.get(routingKey);
            if (matching != null && !matching.isEmpty()) {
                for (RabbitMqInfo c : matching) {
                    links.add(TopicLink.resolved(brokerType(), routingKey, p.className(), p.filePath(), c.className(), c.filePath()));
                    matchedConsumers.add(c);
                }
            } else {
                links.add(TopicLink.orphanProducer(brokerType(), routingKey, p.className(), p.filePath()));
            }
        }

        for (RabbitMqInfo c : consumers) {
            if (!matchedConsumers.contains(c)) {
                for (String queue : splitTopics(c.queues())) {
                    links.add(TopicLink.orphanConsumer(brokerType(), queue.trim(), c.className(), c.filePath()));
                }
            }
        }

        return links;
    }

    private static String[] splitTopics(String topics) {
        if (topics == null || topics.isBlank()) return new String[0];
        return topics.split("\\s*,\\s*");
    }
}
