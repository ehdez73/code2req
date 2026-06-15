package com.github.ehdez73.code2req.analyzer.kafka;

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
public class KafkaTopicLinkResolver implements TopicLinkResolverStrategy {

    @Override
    public String brokerType() {
        return "KAFKA";
    }

    @Override
    public List<TopicLink> resolve(List<AnalysisResult> allResults) {
        List<KafkaPublisherInfo> producers = new ArrayList<>();
        List<KafkaInfo> consumers = new ArrayList<>();
        for (AnalysisResult r : allResults) {
            producers.addAll(r.findings(KafkaPublisherInfo.class));
            consumers.addAll(r.findings(KafkaInfo.class));
        }
        return resolve(producers, consumers);
    }

    List<TopicLink> resolve(List<KafkaPublisherInfo> producers, List<KafkaInfo> consumers) {
        List<TopicLink> links = new ArrayList<>();

        Map<String, List<KafkaInfo>> consumerByTopic = new HashMap<>();
        for (KafkaInfo c : consumers) {
            if (c.isPattern()) continue;
            for (String topic : splitTopics(c.topics())) {
                consumerByTopic.computeIfAbsent(topic.trim(), k -> new ArrayList<>()).add(c);
            }
        }

        Set<KafkaInfo> matchedConsumers = new HashSet<>();
        for (KafkaPublisherInfo p : producers) {
            String topic = p.topic();
            List<KafkaInfo> matching = consumerByTopic.get(topic);
            if (matching != null && !matching.isEmpty()) {
                for (KafkaInfo c : matching) {
                    links.add(TopicLink.resolved(brokerType(), topic, p.className(), p.filePath(), c.className(), c.filePath()));
                    matchedConsumers.add(c);
                }
            } else {
                links.add(TopicLink.orphanProducer(brokerType(), topic, p.className(), p.filePath()));
            }
        }

        for (KafkaInfo c : consumers) {
            if (!matchedConsumers.contains(c)) {
                if (c.isPattern()) {
                    links.add(TopicLink.orphanConsumer(brokerType(), c.topics(), c.className(), c.filePath()));
                } else {
                    for (String topic : splitTopics(c.topics())) {
                        links.add(TopicLink.orphanConsumer(brokerType(), topic.trim(), c.className(), c.filePath()));
                    }
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
