package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SemanticEnrichment {

    private final Map<String, ExecutionFinding> enrichedByFilePath;

    public SemanticEnrichment() {
        this.enrichedByFilePath = new HashMap<>();
    }

    public SemanticEnrichment(Map<String, ExecutionFinding> enrichedByFilePath) {
        this.enrichedByFilePath = Collections.unmodifiableMap(new HashMap<>(enrichedByFilePath));
    }

    public Optional<ExecutionFinding> findByFilePath(String path) {
        return Optional.ofNullable(enrichedByFilePath.get(path));
    }

    public List<ExecutionFinding> all() {
        return List.copyOf(enrichedByFilePath.values());
    }

    public int size() { return enrichedByFilePath.size(); }

    public List<ExecutionFinding.TestInsight> getAllTestInsights() {
        return enrichedByFilePath.values().stream()
            .flatMap(ef -> ef.testInsights().stream())
            .collect(Collectors.toList());
    }

    public Optional<String> getTestFilePath(String filePath) {
        return findByFilePath(filePath)
            .flatMap(ef -> ef.testInsights().stream()
                .map(ExecutionFinding.TestInsight::testFilePath)
                .filter(Objects::nonNull)
                .findFirst());
    }

    public static SemanticEnrichment reloadFrom(ExecutionFindingStore store, ObjectMapper mapper) {
        Map<String, ExecutionFinding> byPath = new HashMap<>();
        var rows = store.findAllByType(FindingType.SEMANTIC_ENRICHMENT);
        for (var row : rows) {
            String json = (String) row.get("finding_json");
            if (json == null) continue;
            try {
                ExecutionFinding ef = mapper.readValue(json, ExecutionFinding.class);
                if (ef.metadata() != null && ef.metadata().filePath() != null) {
                    byPath.put(ef.metadata().filePath(), ef);
                }
            } catch (Exception ignored) {}
        }
        return new SemanticEnrichment(byPath);
    }

    public Map<String, String> getAllTestFilePaths() {
        return enrichedByFilePath.entrySet().stream()
            .filter(e -> e.getValue().testInsights() != null)
            .flatMap(e -> e.getValue().testInsights().stream()
                .map(ti -> Map.entry(e.getKey(), ti.testFilePath())))
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> v1
            ));
    }
}
