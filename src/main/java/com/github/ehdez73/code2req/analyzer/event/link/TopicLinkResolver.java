package com.github.ehdez73.code2req.analyzer.event.link;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicLinkResolver {

    private static final Logger log = LoggerFactory.getLogger(TopicLinkResolver.class);

    private final List<TopicLinkResolverStrategy> strategies;

    public TopicLinkResolver() {
        this.strategies = List.of();
    }

    public TopicLinkResolver(List<TopicLinkResolverStrategy> strategies) {
        this.strategies = strategies;
    }

    public List<TopicLink> resolve(List<AnalysisResult> allResults) {
        List<TopicLink> links = strategies.stream()
            .flatMap(s -> s.resolve(allResults).stream())
            .toList();

        log.info("TopicLinkResolver: {} link(s) resolved ({} RESOLVED, {} PENDING)",
            links.size(),
            links.stream().filter(l -> TopicLink.STATUS_RESOLVED.equals(l.resolvedStatus())).count(),
            links.stream().filter(l -> TopicLink.STATUS_PENDING.equals(l.resolvedStatus())).count());

        return links;
    }
}
