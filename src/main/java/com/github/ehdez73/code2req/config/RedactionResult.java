package com.github.ehdez73.code2req.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RedactionResult {
    private final String redactedContent;
    private final Map<String, Integer> counts;

    public RedactionResult(String redactedContent, Map<String, Integer> counts) {
        this.redactedContent = redactedContent;
        this.counts = new HashMap<>(counts);
    }

    public String redactedContent() {
        return redactedContent;
    }

    public Map<String, Integer> counts() {
        return Collections.unmodifiableMap(counts);
    }

    public int totalRedactions() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
