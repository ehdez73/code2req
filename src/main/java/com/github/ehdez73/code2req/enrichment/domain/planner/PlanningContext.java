package com.github.ehdez73.code2req.enrichment.domain.planner;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlanningContext {

    private final JdbcTemplate jdbc;
    private final Map<String, Set<String>> findingTypeCache = new HashMap<>();

    public PlanningContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean taskHasFindingType(String taskId, String findingType) {
        return getTaskIdsForFindingType(findingType).contains(taskId);
    }

    private Set<String> getTaskIdsForFindingType(String findingType) {
        return findingTypeCache.computeIfAbsent(findingType, ft ->
            new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT task_id FROM execution_findings WHERE finding_type = ?",
                String.class, ft)));
    }
}
