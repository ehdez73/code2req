package com.github.ehdez73.code2req.analyzer.event.broker.activemq;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLinkResolverStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ActiveMqTopicLinkResolver implements TopicLinkResolverStrategy {

    @Override
    public String brokerType() {
        return "ACTIVEMQ";
    }

    @Override
    public List<TopicLink> resolve(List<AnalysisResult> allResults) {
        List<ActiveMqPublisherInfo> producers = new ArrayList<>();
        List<ActiveMqInfo> consumers = new ArrayList<>();
        for (AnalysisResult r : allResults) {
            producers.addAll(r.findings(ActiveMqPublisherInfo.class));
            consumers.addAll(r.findings(ActiveMqInfo.class));
        }
        return resolve(producers, consumers);
    }

    List<TopicLink> resolve(List<ActiveMqPublisherInfo> producers, List<ActiveMqInfo> consumers) {
        List<TopicLink> links = new ArrayList<>();

        Map<String, List<ActiveMqInfo>> consumerByDest = new HashMap<>();
        for (ActiveMqInfo c : consumers) {
            consumerByDest.computeIfAbsent(c.destination(), k -> new ArrayList<>()).add(c);
        }

        Set<ActiveMqInfo> matchedConsumers = new HashSet<>();
        for (ActiveMqPublisherInfo p : producers) {
            String dest = p.destination();
            List<ActiveMqInfo> matching = consumerByDest.get(dest);
            if (matching != null && !matching.isEmpty()) {
                for (ActiveMqInfo c : matching) {
                    links.add(TopicLink.resolved(brokerType(), dest, p.className(), p.filePath(), c.className(), c.filePath()));
                    matchedConsumers.add(c);
                }
            } else {
                links.add(TopicLink.orphanProducer(brokerType(), dest, p.className(), p.filePath()));
            }
        }

        for (ActiveMqInfo c : consumers) {
            if (!matchedConsumers.contains(c)) {
                links.add(TopicLink.orphanConsumer(brokerType(), c.destination(), c.className(), c.filePath()));
            }
        }

        return links;
    }
}
