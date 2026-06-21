package com.github.ehdez73.code2req.store;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExecutionFindingStore {

    private static final Logger log = LoggerFactory.getLogger(ExecutionFindingStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public ExecutionFindingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String taskId, String findingType, String findingJson, boolean resolved) {
        jdbc.update("""
            INSERT INTO execution_findings (task_id, finding_type, finding_json, resolved)
            VALUES (?, ?, ?, ?)
        """, taskId, findingType, findingJson, resolved ? 1 : 0);
    }

    public void saveAllForTask(String taskId, List<? extends AnalysisFinding> findings, String findingType) {
        for (AnalysisFinding finding : findings) {
            try {
                String json = MAPPER.writeValueAsString(finding);
                save(taskId, findingType, json, finding.isResolved());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize finding {} for task {}: {}", findingType, taskId, e.getMessage());
            }
        }
    }

    public void deleteByTaskId(String taskId) {
        jdbc.update("DELETE FROM execution_findings WHERE task_id = ?", taskId);
    }

    public void deleteAll() {
        jdbc.execute("DELETE FROM execution_findings");
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM execution_findings", Integer.class);
        return count != null ? count : 0;
    }

    public int countByType(String findingType) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM execution_findings WHERE finding_type = ?", Integer.class, findingType);
        return count != null ? count : 0;
    }

    public int countByTaskIdAndType(String taskId, String findingType) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM execution_findings WHERE task_id = ? AND finding_type = ?",
            Integer.class, taskId, findingType);
        return count != null ? count : 0;
    }
}