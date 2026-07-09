package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;

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

    public List<ExecutionFinding.HappyPath> getAllHappyPaths() {
        return enrichedByFilePath.values().stream()
            .filter(ef -> ef.businessAbstraction() != null)
            .flatMap(ef -> ef.businessAbstraction().happyPaths().stream())
            .collect(Collectors.toList());
    }

    public List<String> getFlowNames() {
        return getAllHappyPaths().stream()
            .map(ExecutionFinding.HappyPath::flowName)
            .distinct()
            .collect(Collectors.toList());
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
