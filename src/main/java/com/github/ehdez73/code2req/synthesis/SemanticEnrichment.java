package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.model.ExecutionFinding;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
}
