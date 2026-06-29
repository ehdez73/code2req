package com.github.ehdez73.code2req.extraction;

import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinkRegistryTest {

    @Test
    void emptyConstructor() {
        var registry = new LinkRegistry();
        assertTrue(registry.floatingLinks().isEmpty());
        assertTrue(registry.topicLinks().isEmpty());
        assertEquals(0, registry.unresolvedCount());
    }

    @Test
    void storesFloatingAndTopicLinks() {
        var floating = List.of(
            new FloatingLinkInfo("GET", "http://example.com", false, "RT",
                "src/A.java", "m", null, 0.0, "PENDING"));
        var topics = List.of(
            TopicLink.resolved("kafka", "topic", "Prod", "/src/Prod.java", "Cons", "/src/Cons.java"));

        var registry = new LinkRegistry(floating, topics);

        assertEquals(1, registry.floatingLinks().size());
        assertEquals(1, registry.topicLinks().size());
    }

    @Test
    void findUnresolvedFloatingLinksFiltersByPending() {
        var floating = List.of(
            new FloatingLinkInfo("GET", "http://a.com", false, "RT",
                "src/A.java", "m", null, 0.0, "PENDING"),
            new FloatingLinkInfo("POST", "/internal", false, "WC",
                "src/B.java", "n", "/target", 1.0, "RESOLVED"),
            new FloatingLinkInfo("PUT", "http://c.com", false, "RT",
                "src/C.java", "o", null, 0.0, "PENDING"));

        var registry = new LinkRegistry(floating, List.of());

        var unresolved = registry.findUnresolvedFloatingLinks();
        assertEquals(2, unresolved.size());
        assertTrue(unresolved.stream().allMatch(l -> "PENDING".equals(l.resolvedStatus())));
    }

    @Test
    void findUnresolvedTopicLinksFiltersByPending() {
        var topics = List.of(
            TopicLink.resolved("kafka", "orders", "P1", "src/P1.java", "C1", "src/C1.java"),
            TopicLink.orphanProducer("rabbitmq", "alerts", "P2", "src/P2.java"),
            TopicLink.resolved("kafka", "payments", "P3", "src/P3.java", "C3", "src/C3.java"));

        var registry = new LinkRegistry(List.of(), topics);

        var unresolved = registry.findUnresolvedTopicLinks();
        assertEquals(1, unresolved.size());
        assertTrue(unresolved.stream().allMatch(l -> "PENDING".equals(l.resolvedStatus())));
    }

    @Test
    void unresolvedCountSumsBoth() {
        var floating = List.of(
            new FloatingLinkInfo("GET", "http://a.com", false, "RT",
                "src/A.java", "m", null, 0.0, "PENDING"),
            new FloatingLinkInfo("POST", "/internal", false, "WC",
                "src/B.java", "n", "/target", 1.0, "RESOLVED"));
        var topics = List.of(
            TopicLink.resolved("kafka", "orders", "P1", "src/P1.java", "C1", "src/C1.java"),
            TopicLink.orphanConsumer("rabbitmq", "alerts", "C2", "src/C2.java"));

        var registry = new LinkRegistry(floating, topics);

        assertEquals(2, registry.unresolvedCount());
    }

    @Test
    void unresolvedCountReturnsZeroWhenAllResolved() {
        var floating = List.of(
            new FloatingLinkInfo("GET", "/internal", false, "RT",
                "src/A.java", "m", "/target", 1.0, "RESOLVED"));
        var topics = List.of(
            TopicLink.resolved("kafka", "orders", "P1", "src/P1.java", "C1", "src/C1.java"));

        var registry = new LinkRegistry(floating, topics);

        assertEquals(0, registry.unresolvedCount());
    }

    @Test
    void listsAreUnmodifiable() {
        var floating = List.of(
            new FloatingLinkInfo("GET", "http://a.com", false, "RT",
                "src/A.java", "m", null, 0.0, "PENDING"));
        var topics = List.of(
            TopicLink.resolved("kafka", "t", "P", "/src/P.java", "C", "/src/C.java"));

        var registry = new LinkRegistry(floating, topics);

        assertThrows(UnsupportedOperationException.class, () -> registry.floatingLinks().add(null));
        assertThrows(UnsupportedOperationException.class, () -> registry.topicLinks().add(null));
    }
}
