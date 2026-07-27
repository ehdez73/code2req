package com.github.ehdez73.code2req.extraction.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StructuralContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(StructuralContextAssembler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> EXCLUDED_TYPES = Set.of(
        FindingType.SEMANTIC_ENRICHMENT,
        FindingType.FLOW_ANALYSIS,
        FindingType.FLOW_GROUPING
    );

    private final ExecutionFindingStore executionFindingStore;

    public StructuralContextAssembler(ExecutionFindingStore executionFindingStore) {
        this.executionFindingStore = executionFindingStore;
    }

    public String assemble(String taskId) {
        var rows = executionFindingStore.findByTaskId(taskId);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, List<JsonNode>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            String type = (String) row.get("finding_type");
            if (type == null || EXCLUDED_TYPES.contains(type)) {
                continue;
            }
            String json = (String) row.get("finding_json");
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                JsonNode node = MAPPER.readTree(json);
                grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(node);
            } catch (Exception e) {
                log.debug("Failed to parse finding_json for task {} type {}: {}", taskId, type, e.getMessage());
            }
        }

        if (grouped.isEmpty()) {
            return null;
        }

        ObjectNode root = MAPPER.createObjectNode();
        for (var entry : grouped.entrySet()) {
            String key = entry.getKey().toLowerCase();
            ArrayNode array = root.putArray(key);
            for (JsonNode node : entry.getValue()) {
                array.add(node);
            }
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to serialize structural context for task {}: {}", taskId, e.getMessage());
            return null;
        }
    }
}
