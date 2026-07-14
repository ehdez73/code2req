package com.github.ehdez73.code2req.infrastructure.persistence;

import com.github.ehdez73.code2req.common.port.FloatingLinkRepository;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FloatingLinkStore implements FloatingLinkRepository {

    private static final Logger log = LoggerFactory.getLogger(FloatingLinkStore.class);

    private final JdbcTemplate jdbc;
    private final RowMapper<FloatingLinkInfo> rowMapper = (rs, rowNum) -> new FloatingLinkInfo(
        rs.getString("method"),
        rs.getString("url_or_path"),
        rs.getInt("is_expression") == 1,
        rs.getString("client_type"),
        rs.getString("source_task_id"),
        rs.getString("source_method"),
        rs.getString("target_endpoint"),
        rs.getDouble("confidence"),
        rs.getString("resolved_status")
    );

    public FloatingLinkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FloatingLinkInfo link) {
        jdbc.update("""
            INSERT INTO floating_links (method, url_or_path, is_expression, source_task_id, client_type, source_method, target_endpoint, confidence, resolved_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, link.method(), link.urlPattern(), link.isExpression() ? 1 : 0,
            link.sourceFilePath(), link.clientType(), link.sourceMethod(),
            link.targetEndpoint(), link.confidence(), link.resolvedStatus());
    }

    public void saveAll(List<FloatingLinkInfo> links) {
        for (FloatingLinkInfo link : links) {
            jdbc.update("""
                INSERT INTO floating_links (method, url_or_path, is_expression, source_task_id, client_type, source_method, target_endpoint, confidence, resolved_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, link.method(), link.urlPattern(), link.isExpression() ? 1 : 0,
                link.sourceFilePath(), link.clientType(), link.sourceMethod(),
                link.targetEndpoint(), link.confidence(), link.resolvedStatus());
        }
        if (!links.isEmpty()) {
            log.info("Persisted {} floating link(s)", links.size());
        }
    }

    public List<FloatingLinkInfo> findAll() {
        return jdbc.query("SELECT * FROM floating_links", rowMapper);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM floating_links", Integer.class);
        return count != null ? count : 0;
    }

    public void deleteAll() {
        jdbc.execute("DELETE FROM floating_links");
    }

    public List<String> findSourceFilePathsByResolvedStatus(String status) {
        return jdbc.queryForList(
            "SELECT DISTINCT source_task_id FROM floating_links WHERE resolved_status = ?",
            String.class, status);
    }

    public void deleteByTaskId(String taskId) {
        jdbc.update("DELETE FROM floating_links WHERE source_task_id = ?", taskId);
    }

    public int countByTaskId(String taskId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM floating_links WHERE source_task_id = ?",
            Integer.class, taskId);
        return count != null ? count : 0;
    }
}