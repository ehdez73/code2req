package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.common.port.ExecutionFindingRepository;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ExecutionFindingStore implements ExecutionFindingRepository {

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

    @Override
    public void saveAllForTask(String taskId, List<AnalysisFinding> findings) {
        for (AnalysisFinding finding : findings) {
            try {
                String json = MAPPER.writeValueAsString(finding);
                save(taskId, "UNKNOWN_TYPE", json, finding.isResolved());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize finding for task {}: {}", taskId, e.getMessage());
            }
        }
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

    public int countByTaskId(String taskId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM execution_findings WHERE task_id = ?",
            Integer.class, taskId);
        return count != null ? count : 0;
    }

    public int countByTaskIdAndType(String taskId, String findingType) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM execution_findings WHERE task_id = ? AND finding_type = ?",
            Integer.class, taskId, findingType);
        return count != null ? count : 0;
    }

    public List<Map<String, Object>> findByTaskId(String taskId) {
        return jdbc.queryForList("""
            SELECT id, task_id, finding_type, finding_json, resolved, schema_version, created_at
            FROM execution_findings WHERE task_id = ? ORDER BY created_at
        """, taskId);
    }

    public List<Map<String, Object>> findByTaskIdAndType(String taskId, String findingType) {
        return jdbc.queryForList("""
            SELECT id, task_id, finding_type, finding_json, resolved, schema_version, created_at
            FROM execution_findings WHERE task_id = ? AND finding_type = ? ORDER BY created_at
        """, taskId, findingType);
    }

    public boolean existsByFilePathAndType(String filePath, String findingType) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM execution_findings ef
            JOIN tasks t ON t.task_id = ef.task_id
            WHERE t.file_path = ? AND ef.finding_type = ?
        """, Integer.class, filePath, findingType);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> findAllByType(String findingType) {
        return jdbc.queryForList("""
            SELECT id, task_id, finding_type, finding_json, resolved, schema_version, created_at
            FROM execution_findings WHERE finding_type = ? ORDER BY created_at
        """, findingType);
    }

    public void deleteAllByType(String findingType) {
        jdbc.update("DELETE FROM execution_findings WHERE finding_type = ?", findingType);
    }

    public int deleteOrphanedSemanticEnrichment() {
        return jdbc.update("""
            DELETE FROM execution_findings
            WHERE finding_type = ? AND task_id IN (
                SELECT task_id FROM tasks WHERE status = ?
            )
        """, FindingType.SEMANTIC_ENRICHMENT, TaskStatus.INDEXED.name());
    }
}