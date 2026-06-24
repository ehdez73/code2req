package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LinkRegistry {

    private final List<FloatingLinkInfo> floatingLinks;
    private final List<TopicLink> topicLinks;

    public LinkRegistry() {
        this.floatingLinks = List.of();
        this.topicLinks = List.of();
    }

    public LinkRegistry(List<FloatingLinkInfo> floatingLinks, List<TopicLink> topicLinks) {
        this.floatingLinks = Collections.unmodifiableList(floatingLinks);
        this.topicLinks = Collections.unmodifiableList(topicLinks);
    }

    public List<FloatingLinkInfo> floatingLinks() { return floatingLinks; }
    public List<TopicLink> topicLinks() { return topicLinks; }

    public List<FloatingLinkInfo> findUnresolvedFloatingLinks() {
        return floatingLinks.stream()
            .filter(l -> FloatingLinkInfo.STATUS_PENDING.equals(l.resolvedStatus()))
            .collect(Collectors.toList());
    }

    public List<TopicLink> findUnresolvedTopicLinks() {
        return topicLinks.stream()
            .filter(l -> TopicLink.STATUS_PENDING.equals(l.resolvedStatus()))
            .collect(Collectors.toList());
    }

    public int unresolvedCount() {
        return findUnresolvedFloatingLinks().size() + findUnresolvedTopicLinks().size();
    }
}
